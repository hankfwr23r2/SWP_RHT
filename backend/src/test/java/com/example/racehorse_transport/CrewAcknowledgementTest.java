package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Đơn chỉ "Sẵn sàng đón ngựa" (ready_for_pickup) khi đủ hai điều kiện: Chuyên viên đã hoàn tất giấy tờ
// VÀ cả tài xế lẫn hộ tống của mọi xe đã bấm "Tôi đã nhận lệnh". Hoàn tất giấy tờ trước khi kíp xe nhận lệnh
// thì đơn dừng ở "Giấy tờ đã xong" (clearance_done) và hệ thống không tự ghi là kíp đã nhận lệnh.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CrewAcknowledgementTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;
    @Autowired
    private UserRepository users;
    @Autowired
    private StaffRepository staff;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private BookingRepository bookings;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String specialistToken;
    private String specialistCode;
    private String driverToken;
    private String driverCode;
    private String escortToken;
    private String escortCode;
    private Customer customer;

    @BeforeEach
    void people() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        specialistCode = "SP-" + suffix;
        specialistToken = staffToken("chuyenvien" + suffix, "SPECIALIST", specialistCode);
        driverCode = "DR-" + suffix;
        escortCode = "ES-" + suffix;
        driverToken = staffToken("taixe" + suffix, "DRIVER", driverCode);
        escortToken = staffToken("hotong" + suffix, "ESCORT", escortCode);

        User customerUser = new User();
        customerUser.setUsername("khach" + suffix);
        customerUser.setEmail("khach" + suffix + "@example.com");
        customerUser.setPasswordHash(passwordEncoder.encode("Test@123"));
        customerUser = users.save(customerUser);
        customer = new Customer();
        customer.setUser(customerUser);
        customer.setFullName("Khach Test");
        customer = customers.save(customer);
    }

    @Test
    void documentsDoneBeforeTheCrewAcknowledgesStopsAtClearanceDone() throws Exception {
        Booking booking = booking("");

        HttpResponse<String> response = completeClearance(booking);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("clearance_done");
        assertThat(json.readTree(saved.getDetailsJson()).path("clearance").path("crewAckedAt").isMissingNode()).isTrue();
    }

    @Test
    void becomesReadyOnlyAfterBothDriverAndEscortAcknowledge() throws Exception {
        Booking booking = booking("");
        assertThat(completeClearance(booking).statusCode()).isEqualTo(200);

        assertThat(acknowledge(booking, driverToken, "driver").statusCode()).isEqualTo(200);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("clearance_done");

        assertThat(acknowledge(booking, escortToken, "escort").statusCode()).isEqualTo(200);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("ready_for_pickup");
    }

    @Test
    void crewThatAcknowledgedEarlyMakesTheBookingReadyAsSoonAsDocumentsAreDone() throws Exception {
        long earlier = Instant.now().minus(2, ChronoUnit.HOURS).toEpochMilli();
        Booking booking = booking(",\"acks\":{\"driver\":%d,\"escort\":%d}".formatted(earlier, earlier));

        HttpResponse<String> response = completeClearance(booking);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("ready_for_pickup");
    }

    private String staffToken(String username, String role, String code) throws Exception {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("Test@123"));
        user = users.save(user);
        Staff member = new Staff();
        member.setUser(user);
        member.setRole(role);
        member.setStaffCode(code);
        member.setEmploymentStatus("ACTIVE");
        member.setHireDate(LocalDate.now());
        staff.save(member);
        HttpResponse<String> login = post("/auth/staff/login", null,
                "{\"username\":\"%s\",\"password\":\"Test@123\"}".formatted(user.getEmail()));
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        return json.readTree(login.body()).path("data").path("accessToken").asText();
    }

    // Đơn trong nước, giấy tờ đã nộp đủ (chờ Chuyên viên bấm hoàn tất), một xe có tài xế và hộ tống
    private Booking booking(String tripAcks) {
        String details = ("{\"intake\":{\"at\":1,\"by\":\"Quản lý\",\"specialist\":{\"id\":\"%s\",\"name\":\"Chuyên viên\"},"
                + "\"coordinator\":{\"id\":\"CO-1\",\"name\":\"Điều phối\"}},"
                + "\"clearance\":{\"items\":[{\"type\":\"health_cert\",\"status\":\"done\"},{\"type\":\"poa\",\"status\":\"done\"}]},"
                + "\"trips\":[{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[\"H-1\"],\"driverId\":\"%s\",\"escortId\":\"%s\"%s}]}")
                .formatted(specialistCode, driverCode, escortCode, tripAcks);
        return bookings.save(Booking.builder()
                .customerID(customer)
                .status("clearance_in_progress")
                .bookingType("domestic")
                .bookingDate(Instant.now().minus(20, ChronoUnit.DAYS))
                .departureDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .pickupAddress("Kho Đồng Nai")
                .dropoffAddress("Hà Nội")
                .detailsJson(details)
                .build());
    }

    private HttpResponse<String> completeClearance(Booking booking) throws Exception {
        return post("/specialist/bookings/" + booking.getId() + "/complete-clearance", specialistToken, "{}");
    }

    private HttpResponse<String> acknowledge(Booking booking, String token, String who) throws Exception {
        return post("/field/bookings/" + booking.getId() + "/trip-update", token,
                "{\"tripId\":\"TRP-1\",\"action\":\"ACKNOWLEDGE\",\"actor\":\"Kíp xe\",\"payload\":{\"who\":\"%s\"}}".formatted(who));
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
