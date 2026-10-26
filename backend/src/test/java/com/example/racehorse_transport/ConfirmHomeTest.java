package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
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

// Tài xế bấm "Đã về trụ sở" (CONFIRM_HOME) chỉ được ghi nhận khi xe đã giao ngựa xong (có chặng về trụ sở),
// đã qua mốc sớm nhất có thể về tới nơi, và có ảnh chụp tại trụ sở; ảnh được lưu cùng mốc về.
// Mọi trường hợp khác bị từ chối và đơn giữ nguyên.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfirmHomeTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final String PHOTO = "https://files.example.com/tru-so.jpg";

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
    private String driverToken;
    private Customer customer;

    @BeforeEach
    void driverAndCustomer() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User driverUser = new User();
        driverUser.setUsername("taixe" + suffix);
        driverUser.setEmail("taixe" + suffix + "@example.com");
        driverUser.setPasswordHash(passwordEncoder.encode("Test@123"));
        driverUser = users.save(driverUser);
        Staff driver = new Staff();
        driver.setUser(driverUser);
        driver.setRole("DRIVER");
        driver.setStaffCode("DR-" + suffix);
        driver.setEmploymentStatus("ACTIVE");
        driver.setHireDate(LocalDate.now());
        staff.save(driver);

        User customerUser = new User();
        customerUser.setUsername("khach" + suffix);
        customerUser.setEmail("khach" + suffix + "@example.com");
        customerUser.setPasswordHash(passwordEncoder.encode("Test@123"));
        customerUser = users.save(customerUser);
        customer = new Customer();
        customer.setUser(customerUser);
        customer.setFullName("Khach Test");
        customer = customers.save(customer);

        HttpResponse<String> login = post("/auth/staff/login", null,
                "{\"username\":\"%s\",\"password\":\"Test@123\"}".formatted(driverUser.getEmail()));
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        driverToken = json.readTree(login.body()).path("data").path("accessToken").asText();
    }

    @Test
    void rejectedWhileTheHorsesAreStillOnTheWay() throws Exception {
        Booking booking = bookingWithTrip("in_transit", "\"run\":{\"startedAt\":%d}".formatted(epoch(-3)));

        HttpResponse<String> response = confirmHome(booking, PHOTO);

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(homeReturnOf(booking).path("backAt").isMissingNode()).isTrue();
    }

    @Test
    void rejectedBeforeTheEarliestPossibleArrival() throws Exception {
        Booking booking = bookingWithTrip("delivered_pending_settlement", homeReturn(epoch(2)));

        HttpResponse<String> response = confirmHome(booking, PHOTO);

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(homeReturnOf(booking).path("backAt").isMissingNode()).isTrue();
    }

    @Test
    void rejectedWithoutAPhotoTakenAtHq() throws Exception {
        Booking booking = bookingWithTrip("delivered_pending_settlement", homeReturn(epoch(-1)));

        HttpResponse<String> response = confirmHome(booking, null);

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(homeReturnOf(booking).path("backAt").isMissingNode()).isTrue();
    }

    @Test
    void recordedWithThePhotoOnceDeliveredAndBackInTime() throws Exception {
        Booking booking = bookingWithTrip("delivered_pending_settlement", homeReturn(epoch(-1)));

        HttpResponse<String> response = confirmHome(booking, PHOTO);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode homeReturn = homeReturnOf(booking);
        assertThat(homeReturn.path("backAt").asLong()).isPositive();
        assertThat(homeReturn.path("photo").asText()).isEqualTo(PHOTO);
    }

    private static long epoch(long hoursFromNow) {
        return Instant.now().plus(hoursFromNow, ChronoUnit.HOURS).toEpochMilli();
    }

    private static String homeReturn(long earliestAt) {
        return "\"run\":{\"startedAt\":%d,\"deliveredAt\":%d},\"homeReturn\":{\"fromPlace\":\"Hà Nội\",\"earliestAt\":%d,\"eta\":%d}"
                .formatted(epoch(-10), epoch(-5), earliestAt, earliestAt + 3_600_000L);
    }

    private Booking bookingWithTrip(String status, String tripState) {
        Booking booking = Booking.builder()
                .customerID(customer)
                .status(status)
                .bookingType("domestic")
                .bookingDate(Instant.now().minus(30, ChronoUnit.DAYS))
                .departureDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .pickupAddress("Kho Đồng Nai")
                .dropoffAddress("Hà Nội")
                .detailsJson("{\"trips\":[{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[\"H-1\"],%s}]}".formatted(tripState))
                .build();
        return bookings.save(booking);
    }

    private JsonNode homeReturnOf(Booking booking) throws Exception {
        String details = bookings.findById(booking.getId()).orElseThrow().getDetailsJson();
        return json.readTree(details).path("trips").path(0).path("homeReturn");
    }

    private HttpResponse<String> confirmHome(Booking booking, String photo) throws Exception {
        String payload = photo == null ? "{}" : "{\"photo\":\"%s\"}".formatted(photo);
        return post("/field/bookings/" + booking.getId() + "/trip-update", driverToken,
                "{\"tripId\":\"TRP-1\",\"action\":\"CONFIRM_HOME\",\"actor\":\"Tài xế\",\"payload\":%s}".formatted(payload));
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
