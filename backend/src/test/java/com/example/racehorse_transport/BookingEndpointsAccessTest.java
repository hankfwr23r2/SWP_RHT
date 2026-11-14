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

// Ai được gọi những API đặt chuyến, báo giá và tra cứu đơn:
// - Tra cứu công khai chỉ trả đơn khi khớp 4 số cuối điện thoại, không cần đăng nhập, không lộ đơn khi sai số.
// - API của Quản lý chỉ Quản lý/Quản trị gọi được; API khách chỉ khách gọi được; chuyến được giao chỉ Tài xế/Hộ tống.
// - Khách không từ chối báo giá hay gửi lại hồ sơ thay đơn của khách khác.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockTurnstileVerification
class BookingEndpointsAccessTest {

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
    private String managerToken;
    private String driverToken;
    private String ownerToken;
    private String strangerToken;
    private Customer owner;

    @BeforeEach
    void accounts() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        managerToken = staffLogin("quanly" + suffix, "MANAGER", "MG-" + suffix);
        driverToken = staffLogin("taixe" + suffix, "DRIVER", "DR-" + suffix);
        owner = customer("chu" + suffix, "0912345678");
        ownerToken = customerLogin(owner);
        strangerToken = customerLogin(customer("la" + suffix, "0900000000"));
    }

    // ===== Tra cứu công khai =====

    @Test
    void publicTrackingFindsTheBookingByCodeAndLastFourDigitsWithoutLogin() throws Exception {
        Booking booking = booking("awaiting_payment", "0987651234");

        HttpResponse<String> response = get("/bookings/public-track?code=BK-" + booking.getId() + "&phoneLast4=1234", null);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("Kho Đồng Nai → Hà Nội");
    }

    @Test
    void publicTrackingAlsoAcceptsTheCustomersOwnPhone() throws Exception {
        Booking booking = booking("awaiting_payment", null);

        HttpResponse<String> response = get("/bookings/public-track?code=" + booking.getId() + "&phoneLast4=5678", null);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    @Test
    void publicTrackingDoesNotRevealTheBookingWhenTheDigitsAreWrong() throws Exception {
        Booking booking = booking("awaiting_payment", "0987651234");

        HttpResponse<String> response = get("/bookings/public-track?code=BK-" + booking.getId() + "&phoneLast4=0000", null);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("Kho Đồng Nai").doesNotContain("Hà Nội");
    }

    @Test
    void publicTrackingOfAnUnknownCodeIsNotFound() throws Exception {
        HttpResponse<String> response = get("/bookings/public-track?code=BK-99999999&phoneLast4=1234", null);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void batchLookupAnswersEachCodeSeparatelyAndLeaksNothingOnAMismatch() throws Exception {
        Booking mine = booking("awaiting_payment", "0987651234");
        Booking other = booking("awaiting_payment", "0911110000");

        HttpResponse<String> response = post("/bookings/public-track/batch", null,
                "{\"codes\":[\"BK-%d\",\"BK-%d\"],\"phoneLast4\":\"1234\"}".formatted(mine.getId(), other.getId()));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode results = json.readTree(response.body()).path("data");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).path("result").isNull()).isFalse();
        assertThat(results.get(1).path("result").isNull()).isTrue();
    }

    @Test
    void batchLookupRejectsMalformedInput() throws Exception {
        assertThat(post("/bookings/public-track/batch", null, "{\"codes\":[],\"phoneLast4\":\"1234\"}").statusCode()).isEqualTo(400);
        assertThat(post("/bookings/public-track/batch", null, "{\"codes\":[\"BK-1\"],\"phoneLast4\":\"12\"}").statusCode()).isEqualTo(400);
        assertThat(post("/bookings/public-track/batch", null,
                "{\"codes\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"],\"phoneLast4\":\"1234\"}").statusCode()).isEqualTo(400);
    }

    // ===== Phân quyền =====

    @Test
    void managerApisAreClosedToCustomersAndAnonymousUsers() throws Exception {
        Booking booking = booking("under_review", null);

        assertThat(get("/manager/bookings/intake", ownerToken).statusCode()).isEqualTo(403);
        assertThat(post("/manager/bookings/" + booking.getId() + "/send-quote", ownerToken, "{}").statusCode()).isEqualTo(403);
        assertThat(post("/manager/bookings/" + booking.getId() + "/reject", ownerToken, "{\"reason\":\"x\"}").statusCode()).isEqualTo(403);
        assertThat(post("/manager/bookings/maintenance/release-expired", ownerToken, "{}").statusCode()).isEqualTo(403);
        assertThat(get("/manager/bookings/intake", null).statusCode()).isIn(401, 403);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("under_review");
    }

    @Test
    void managerCanListTheIntakeQueue() throws Exception {
        HttpResponse<String> response = get("/manager/bookings/intake", managerToken);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    @Test
    void customerApisAreClosedToStaff() throws Exception {
        Booking booking = booking("awaiting_payment", null);

        assertThat(get("/customer/bookings", managerToken).statusCode()).isEqualTo(403);
        assertThat(post("/customer/bookings/" + booking.getId() + "/reject-quote", managerToken, "{}").statusCode()).isEqualTo(403);
        assertThat(post("/customer/bookings/" + booking.getId() + "/resubmit", driverToken, "{}").statusCode()).isEqualTo(403);
    }

    @Test
    void assignedTripsAreOnlyForDriversAndEscorts() throws Exception {
        assertThat(get("/bookings/assigned", driverToken).statusCode()).isEqualTo(200);
        assertThat(get("/bookings/assigned", ownerToken).statusCode()).isEqualTo(403);
        assertThat(get("/bookings/assigned", managerToken).statusCode()).isEqualTo(403);
    }

    // ===== Đơn của khách khác =====

    @Test
    void aCustomerCannotRejectAnotherCustomersQuote() throws Exception {
        Booking booking = booking("awaiting_payment", null);

        HttpResponse<String> response = post("/customer/bookings/" + booking.getId() + "/reject-quote", strangerToken, "{\"reason\":\"không thích\"}");

        assertThat(response.statusCode()).as(response.body()).isGreaterThanOrEqualTo(400);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("awaiting_payment");
    }

    @Test
    void theOwnerCanStillRejectTheirOwnQuote() throws Exception {
        Booking booking = booking("awaiting_payment", null);

        HttpResponse<String> response = post("/customer/bookings/" + booking.getId() + "/reject-quote", ownerToken, "{\"reason\":\"giá cao\"}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("cancelled");
    }

    @Test
    void aCustomerCannotResubmitAnotherCustomersDocuments() throws Exception {
        Booking booking = booking("schedule_change_required", null);

        HttpResponse<String> response = post("/customer/bookings/" + booking.getId() + "/resubmit", strangerToken, "{}");

        assertThat(response.statusCode()).as(response.body()).isGreaterThanOrEqualTo(400);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("schedule_change_required");
    }

    @Test
    void aCustomerCannotSeeAnotherCustomersTripTeamOrBooking() throws Exception {
        Booking booking = booking("en_route_to_pickup", null);

        assertThat(get("/customer/bookings/" + booking.getId() + "/team", strangerToken).statusCode()).isGreaterThanOrEqualTo(400);
        assertThat(get("/customer/bookings/" + booking.getId(), strangerToken).statusCode()).isGreaterThanOrEqualTo(400);
        assertThat(get("/customer/bookings/" + booking.getId(), ownerToken).statusCode()).isEqualTo(200);
    }

    // ===== Dựng dữ liệu =====

    private Booking booking(String status, String consignorPhone) {
        return bookings.save(Booking.builder()
                .customerID(owner)
                .status(status)
                .bookingType("domestic")
                .bookingDate(Instant.now().minus(5, ChronoUnit.DAYS))
                .departureDate(Instant.now().plus(25, ChronoUnit.DAYS))
                .pickupAddress("Kho Đồng Nai")
                .dropoffAddress("Hà Nội")
                .consignorPhone(consignorPhone)
                .detailsJson("{}")
                .build());
    }

    private Customer customer(String username, String phone) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@gmail.com");
        user.setPasswordHash(passwordEncoder.encode("Test@123"));
        user.setEmailVerified(true);
        user = users.save(user);
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setFullName("Khach " + username);
        customer.setPhone(phone);
        return customers.save(customer);
    }

    private String customerLogin(Customer customer) throws Exception {
        HttpResponse<String> login = post("/auth/customer/login", null,
                "{\"username\":\"%s\",\"password\":\"Test@123\"}".formatted(customer.getUser().getEmail()));
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        return json.readTree(login.body()).path("data").path("accessToken").asText();
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

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
