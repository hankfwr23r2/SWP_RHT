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

// Tài xế chỉ "Xuất phát tới điểm đón" (DEPART_TO_PICKUP) được khi đơn đã sẵn sàng đón (ready_for_pickup),
// tức giấy tờ đã xong. Đơn ở bước khác (chưa tiếp nhận, đang làm giấy tờ…) bị từ chối và giữ nguyên trạng thái.
// Đơn nhiều xe: xe đầu đã xuất phát thì các xe sau vẫn xuất phát được, và trạng thái đơn không bị kéo lùi.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DepartToPickupTest {

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
    void rejectedWhileClearanceDocumentsAreStillInProgress() throws Exception {
        Booking booking = booking("clearance_in_progress");

        HttpResponse<String> response = depart(booking);

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertUnchanged(booking, "clearance_in_progress");
    }

    @Test
    void rejectedBeforeTheBookingIsEvenAccepted() throws Exception {
        Booking booking = booking("pending_intake");

        HttpResponse<String> response = depart(booking);

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertUnchanged(booking, "pending_intake");
    }

    @Test
    void departsOnceTheBookingIsReadyForPickup() throws Exception {
        Booking booking = booking("ready_for_pickup");

        HttpResponse<String> response = depart(booking);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("en_route_to_pickup");
        assertThat(json.readTree(saved.getDetailsJson()).path("trips").path(0).path("departedAt").asLong()).isPositive();
    }

    @Test
    void secondVehicleDepartsAfterTheFirstOneLeft() throws Exception {
        Booking booking = twoTripBooking("en_route_to_pickup", "\"departedAt\":%d".formatted(Instant.now().toEpochMilli()));

        HttpResponse<String> response = depart(booking, "TRP-2");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("en_route_to_pickup");
        assertThat(json.readTree(saved.getDetailsJson()).path("trips").path(1).path("departedAt").asLong()).isPositive();
    }

    @Test
    void secondVehicleDepartingDoesNotMoveTheBookingBackwards() throws Exception {
        long now = Instant.now().toEpochMilli();
        Booking booking = twoTripBooking("in_transit", "\"departedAt\":%d,\"run\":{\"startedAt\":%d}".formatted(now, now));

        HttpResponse<String> response = depart(booking, "TRP-2");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("in_transit");
    }

    private Booking twoTripBooking(String status, String firstTripState) {
        return bookings.save(Booking.builder()
                .customerID(customer)
                .status(status)
                .bookingType("domestic")
                .bookingDate(Instant.now().minus(25, ChronoUnit.DAYS))
                .departureDate(Instant.now())
                .pickupAddress("Kho Đồng Nai")
                .dropoffAddress("Hà Nội")
                .detailsJson(("{\"trips\":[{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[\"H-1\"],%s},"
                        + "{\"tripId\":\"TRP-2\",\"vehicleId\":\"VH-002\",\"horseIds\":[\"H-2\"]}]}").formatted(firstTripState))
                .build());
    }

    private void assertUnchanged(Booking booking, String status) throws Exception {
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(status);
        assertThat(json.readTree(saved.getDetailsJson()).path("trips").path(0).path("departedAt").isMissingNode()).isTrue();
    }

    private Booking booking(String status) {
        return bookings.save(Booking.builder()
                .customerID(customer)
                .status(status)
                .bookingType("domestic")
                .bookingDate(Instant.now().minus(25, ChronoUnit.DAYS))
                .departureDate(Instant.now().plus(1, ChronoUnit.DAYS))
                .pickupAddress("Kho Đồng Nai")
                .dropoffAddress("Hà Nội")
                .detailsJson("{\"trips\":[{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[\"H-1\"]}]}")
                .build());
    }

    private HttpResponse<String> depart(Booking booking) throws Exception {
        return depart(booking, "TRP-1");
    }

    private HttpResponse<String> depart(Booking booking, String tripId) throws Exception {
        return post("/field/bookings/" + booking.getId() + "/trip-update", driverToken,
                "{\"tripId\":\"%s\",\"action\":\"DEPART_TO_PICKUP\",\"actor\":\"Tài xế\",\"payload\":{}}".formatted(tripId));
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
