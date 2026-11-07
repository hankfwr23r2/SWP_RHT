package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
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

// Người nhận không tới điểm giao (quá hạn bàn giao):
// - Chưa đủ 2 giờ thì vẫn báo được, nhưng phải xác nhận rõ ràng (demo không bắt chờ).
// - Chỉ tài xế được phân công, và xe phải đã tới điểm giao.
// - Sự cố có mã chuyến và vị trí để quản lý thấy; giao ngựa xong thì sự cố tự đóng.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockTurnstileVerification
class DeliveryTimeoutTest {

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
    private IncidentRepository incidents;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String driverCode;
    private String driverToken;
    private String otherDriverToken;
    private String escortToken;
    private String escortCode;
    private Customer customer;

    @BeforeEach
    void crewAndCustomer() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        driverCode = "DR-" + suffix;
        escortCode = "ES-" + suffix;
        driverToken = staffLogin("taixe" + suffix, "DRIVER", driverCode);
        otherDriverToken = staffLogin("taixekhac" + suffix, "DRIVER", "DR-X" + suffix);
        escortToken = staffLogin("hotong" + suffix, "ESCORT", escortCode);
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
    void reportingBeforeTwoHoursNeedsAnExplicitConfirmation() throws Exception {
        Booking booking = atDelivery(10, "");

        HttpResponse<String> response = timeout(booking, driverToken, false);

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(messageOf(response)).contains("10/120 phút").contains("xác nhận");
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("in_transit");
        assertThat(incidents.findByBookingID_Id(booking.getId())).isEmpty();
    }

    @Test
    void confirmedEarlyReportIsRecordedWithTripAndLocationForTheManager() throws Exception {
        Booking booking = atDelivery(10, "");

        HttpResponse<String> response = timeout(booking, driverToken, true);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("handover_timeout");
        Incident incident = incidents.findByBookingID_Id(booking.getId()).get(0);
        assertThat(incident.getIncidentType()).isEqualTo("HANDOVER_TIMEOUT");
        assertThat(incident.getStatus()).isEqualTo("active");
        assertThat(incident.getTripId()).isEqualTo("TRP-1");
        assertThat(incident.getLocationLat()).isEqualTo(11.56);
        assertThat(incident.getLocationLng()).isEqualTo(104.92);
        assertThat(incident.getDescription()).contains("báo sớm").contains("10 phút");
    }

    @Test
    void onlyTheAssignedDriverCanReport() throws Exception {
        Booking booking = atDelivery(150, "");

        assertThat(timeout(booking, otherDriverToken, true).statusCode()).isEqualTo(403);
        assertThat(timeout(booking, escortToken, true).statusCode()).isEqualTo(403);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("in_transit");
    }

    @Test
    void cannotReportBeforeArrivingAtTheDeliveryPoint() throws Exception {
        Booking booking = atDelivery(-1, "");

        HttpResponse<String> response = timeout(booking, driverToken, true);

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(messageOf(response)).contains("tới điểm giao");
    }

    @Test
    void afterTwoHoursNoConfirmationIsNeeded() throws Exception {
        Booking booking = atDelivery(130, "");

        HttpResponse<String> response = timeout(booking, driverToken, false);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("handover_timeout");
    }

    @Test
    void completingTheDeliveryLaterClosesTheTimeoutIncident() throws Exception {
        Booking booking = atDelivery(10, ",\"handoverPhoto\":\"https://example.com/bb.jpg\",\"returnedOriginals\":true");
        assertThat(timeout(booking, driverToken, true).statusCode()).isEqualTo(200);

        HttpResponse<String> response = post("/field/bookings/" + booking.getId() + "/trip-update", driverToken,
                "{\"tripId\":\"TRP-1\",\"action\":\"COMPLETE_DELIVERY\",\"actor\":\"Tài xế\",\"payload\":{}}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("delivered_pending_settlement");
        assertThat(incidents.findByBookingID_Id(booking.getId()).get(0).getStatus()).isEqualTo("resolved");
    }

    // Xe đã chạy tới điểm giao; arrivedMinutesAgo < 0 nghĩa là chưa tới
    private Booking atDelivery(int arrivedMinutesAgo, String extraDelivery) {
        long now = Instant.now().toEpochMilli();
        String arrived = arrivedMinutesAgo < 0 ? "" : ",\"arrivedAt\":%d,\"photo\":\"https://example.com/giao.jpg\""
                .formatted(now - arrivedMinutesAgo * 60_000L);
        String details = ("{\"trips\":[{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[],\"driverId\":\"%s\",\"escortId\":\"%s\","
                + "\"departedAt\":%d,\"run\":{\"startedAt\":%d,\"welfare\":[{\"checkpointId\":\"delivery\",\"at\":%d}],\"checkpoints\":["
                + "{\"id\":\"pickup\",\"type\":\"pickup\",\"doneAt\":%d},"
                + "{\"id\":\"delivery\",\"type\":\"delivery\",\"label\":\"Bàn giao\",\"place\":\"Phnom Penh\",\"lat\":11.56,\"lng\":104.92%s%s}]}}]}")
                .formatted(driverCode, escortCode, now, now, now, now, arrived, extraDelivery);
        return bookings.save(Booking.builder()
                .customerID(customer)
                .status("in_transit")
                .bookingType("international")
                .bookingDate(Instant.now().minus(25, ChronoUnit.DAYS))
                .departureDate(Instant.now())
                .pickupAddress("TP.HCM")
                .dropoffAddress("Phnom Penh")
                .detailsJson(details)
                .build());
    }

    private HttpResponse<String> timeout(Booking booking, String token, boolean confirmEarly) throws Exception {
        return post("/delivery/" + booking.getId() + "/timeout", token,
                "{\"tripId\":\"TRP-1\",\"timeoutReason\":\"UNREACHABLE\",\"selectedAction\":\"STABLE_NEARBY\","
                + "\"partnerName\":\"Chuồng tạm\",\"estimatedCost\":1500000,\"note\":\"Gọi không nghe máy\",\"confirmEarly\":" + confirmEarly + "}");
    }

    private String staffLogin(String username, String role, String code) throws Exception {
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

    private String messageOf(HttpResponse<String> response) throws Exception {
        return json.readTree(response.body()).path("message").asText();
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
