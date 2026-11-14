package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
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

// Điều phối viên được sửa phương án xe và lộ trình đã chốt cho tới khi Quản lý gửi báo giá.
// Từ lúc báo giá đã gửi (khách đang cọc, đã có vận đơn…) thì không chốt lại được, trạng thái đơn giữ nguyên.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockTurnstileVerification
class CoordinatorPlanLockTest {

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
    private VehicleRepository vehicles;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String coordinatorToken;
    private Customer customer;
    private Vehicle truck;

    @BeforeEach
    void coordinatorCustomerAndTruck() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername("dieuphoi" + suffix);
        user.setEmail("dieuphoi" + suffix + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("Test@123"));
        user = users.save(user);
        Staff coordinator = new Staff();
        coordinator.setUser(user);
        coordinator.setRole("COORDINATOR");
        coordinator.setStaffCode("CO-" + suffix);
        coordinator.setEmploymentStatus("ACTIVE");
        coordinator.setHireDate(LocalDate.now());
        staff.save(coordinator);
        HttpResponse<String> login = post("/auth/staff/login", null,
                "{\"username\":\"%s\",\"password\":\"Test@123\"}".formatted(user.getEmail()));
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        coordinatorToken = json.readTree(login.body()).path("data").path("accessToken").asText();

        User customerUser = new User();
        customerUser.setUsername("khach" + suffix);
        customerUser.setEmail("khach" + suffix + "@example.com");
        customerUser.setPasswordHash(passwordEncoder.encode("Test@123"));
        customerUser = users.save(customerUser);
        customer = new Customer();
        customer.setUser(customerUser);
        customer.setFullName("Khach Test");
        customer = customers.save(customer);

        truck = vehicles.save(Vehicle.builder()
                .vehicleType("Xe chuyên dụng 4 ngăn")
                .licensePlate("51C-" + suffix.substring(0, 5))
                .status("ACTIVE")
                .build());
    }

    @Test
    void cannotReplanOnceTheQuoteHasBeenSent() throws Exception {
        Booking booking = booking("waybill_issued");

        HttpResponse<String> response = confirmPlan(booking);

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("waybill_issued");
    }

    @Test
    void cannotReplanWhileTheCustomerIsPayingTheDeposit() throws Exception {
        Booking booking = booking("awaiting_payment");

        HttpResponse<String> response = confirmPlan(booking);

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("awaiting_payment");
    }

    @Test
    void canEditTheConfirmedPlanBeforeTheManagerSendsTheQuote() throws Exception {
        Booking booking = booking("pending_commercial");

        HttpResponse<String> response = confirmPlan(booking);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("pending_commercial");
    }

    // ===== Sức chứa và phân ngựa lên xe: backend tự kiểm tra lại, không tin phương án client gửi =====

    @Test
    void extendedStallHorsesTakeTwoStallsEach() throws Exception {
        Booking booking = booking("under_review", horse("H-1", "Storm", "single") + "," + horse("H-2", "Blaze", "single"));
        Vehicle small = truck("2 ngăn");

        HttpResponse<String> response = confirmPlan(booking, trip(small, "H-1", "H-2"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("cần 4 ngăn");
    }

    @Test
    void standardStallHorsesFillTheTruckExactly() throws Exception {
        Booking booking = booking("under_review", horse("H-1", "Storm", "standard") + "," + horse("H-2", "Blaze", "standard"));
        Vehicle small = truck("2 ngăn");

        HttpResponse<String> response = confirmPlan(booking, trip(small, "H-1", "H-2"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    @Test
    void everyHorseOfTheBookingMustBeOnATruck() throws Exception {
        Booking booking = booking("under_review", horse("H-1", "Storm", "standard") + "," + horse("H-2", "Blaze", "standard"));

        HttpResponse<String> response = confirmPlan(booking, trip(truck, "H-1"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("chưa được xếp xe").contains("Blaze");
    }

    @Test
    void aHorseCannotBeOnTwoTrucks() throws Exception {
        Booking booking = booking("under_review", horse("H-1", "Storm", "standard") + "," + horse("H-2", "Blaze", "standard"));

        HttpResponse<String> response = confirmPlan(booking, trip(truck, "H-1", "H-2") + "," + trip(truck("2 ngăn"), "H-1"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("đang được xếp trên nhiều xe");
    }

    @Test
    void horsesOutsideTheBookingAreRejected() throws Exception {
        Booking booking = booking("under_review");

        HttpResponse<String> response = confirmPlan(booking, trip(truck, "H-1", "H-9"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("H-9").contains("không thuộc đơn này");
    }

    @Test
    void oneTruckCannotRunTwoTripsOfTheSameBooking() throws Exception {
        Booking booking = booking("under_review", horse("H-1", "Storm", "standard") + "," + horse("H-2", "Blaze", "standard"));

        HttpResponse<String> response = confirmPlan(booking, trip(truck, "H-1") + "," + trip(truck, "H-2"));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(response.body()).contains("nhiều chuyến");
    }

    private static String horse(String id, String name, String stall) {
        return "{\"horseId\":\"%s\",\"name\":\"%s\",\"stall\":\"%s\"}".formatted(id, name, stall);
    }

    private static String trip(Vehicle vehicle, String... horseIds) {
        return "{\"vehicleId\":\"%d\",\"horseIds\":[\"%s\"]}".formatted(vehicle.getId(), String.join("\",\"", horseIds));
    }

    private Vehicle truck(String stalls) {
        return vehicles.save(Vehicle.builder()
                .vehicleType("Xe chuyên dụng " + stalls)
                .licensePlate("51D-" + UUID.randomUUID().toString().substring(0, 5))
                .status("ACTIVE")
                .build());
    }

    private Booking booking(String status) {
        return booking(status, "{\"horseId\":\"H-1\",\"name\":\"Storm\"}");
    }

    private Booking booking(String status, String horsesJson) {
        String details = "{\"intake\":{\"at\":1,\"by\":\"Quản lý\"},\"medical\":{\"status\":\"approved\"},"
                + "\"horses\":[" + horsesJson + "],"
                + "\"trips\":[{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-999\",\"horseIds\":[\"H-1\"]}],"
                + "\"plan\":{\"at\":1,\"by\":\"Điều phối\",\"note\":\"\"}}";
        return bookings.save(Booking.builder()
                .customerID(customer)
                .status(status)
                .bookingType("domestic")
                .bookingDate(Instant.now().minus(5, ChronoUnit.DAYS))
                .departureDate(Instant.now().plus(25, ChronoUnit.DAYS))
                .pickupAddress("Kho Đồng Nai")
                .dropoffAddress("Hà Nội")
                .detailsJson(details)
                .build());
    }

    private HttpResponse<String> confirmPlan(Booking booking) throws Exception {
        return confirmPlan(booking, trip(truck, "H-1"));
    }

    private HttpResponse<String> confirmPlan(Booking booking, String tripsJson) throws Exception {
        String body = "{\"by\":\"Điều phối\",\"trips\":[" + tripsJson + "],\"note\":\"Đổi xe\"}";
        return post("/coordinator/bookings/" + booking.getId() + "/fleet-plan", coordinatorToken, body);
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
