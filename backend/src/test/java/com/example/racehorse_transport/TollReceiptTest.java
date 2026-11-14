package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.CaptureAsset;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CaptureAssetRepository;
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

// Phí cầu đường không còn ước tính bằng dịch vụ bản đồ: tài xế chụp hóa đơn ở trạm thu phí và ghi số tiền.
// Ghi được từ lúc xe xuất phát tới khi xác nhận về trụ sở (cả chiều về). Nhà xe chịu phí này:
// không cộng vào quyết toán của khách; Quản lý xem tổng phí cầu đường của chuyến trong báo cáo chuyến.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockTurnstileVerification
class TollReceiptTest {

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
    private CaptureAssetRepository captures;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String driverUsername;
    private String driverCode;
    private String driverToken;
    private String otherDriverToken;
    private String managerToken;
    private Customer customer;

    @BeforeEach
    void crewManagerAndCustomer() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        driverCode = "DR-" + suffix;
        User driver = staffAccount("taixe" + suffix, "DRIVER", driverCode);
        driverUsername = driver.getUsername();
        driverToken = login(driver);
        otherDriverToken = login(staffAccount("taixekhac" + suffix, "DRIVER", "DR2-" + suffix));
        managerToken = login(staffAccount("quanly" + suffix, "MANAGER", "MG-" + suffix));

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
    void driverRecordsATollWithItsReceiptOnTheWayToPickup() throws Exception {
        Booking booking = bookingWithTrip("en_route_to_pickup", "\"departedAt\":%d".formatted(epoch(-2)));
        String photo = receipt(booking);

        HttpResponse<String> response = addToll(booking, driverToken, 185000, "Trạm BOT Long Thành", photo);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode toll = tollsOf(booking).path(0);
        assertThat(toll.path("amount").asLong()).isEqualTo(185000);
        assertThat(toll.path("station").asText()).isEqualTo("Trạm BOT Long Thành");
        assertThat(toll.path("photo").asText()).isEqualTo(photo);
        assertThat(toll.path("id").asText()).isNotBlank();
    }

    @Test
    void tollsOnTheWayBackToHqAreRecordedToo() throws Exception {
        Booking booking = bookingWithTrip("delivered_pending_settlement", deliveredAndDrivingBack());

        HttpResponse<String> response = addToll(booking, driverToken, 60000, "Trạm Pháp Vân", receipt(booking));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(tollsOf(booking)).hasSize(1);
    }

    @Test
    void rejectedWithoutAPhotoOfTheReceipt() throws Exception {
        Booking booking = bookingWithTrip("in_transit", "\"departedAt\":%d,\"run\":{\"startedAt\":%d}".formatted(epoch(-4), epoch(-3)));

        HttpResponse<String> response = addToll(booking, driverToken, 185000, "Trạm BOT Long Thành", null);

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(tollsOf(booking)).isEmpty();
    }

    @Test
    void rejectedForAZeroAmount() throws Exception {
        Booking booking = bookingWithTrip("en_route_to_pickup", "\"departedAt\":%d".formatted(epoch(-2)));

        HttpResponse<String> response = addToll(booking, driverToken, 0, "Trạm BOT Long Thành", receipt(booking));

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(tollsOf(booking)).isEmpty();
    }

    @Test
    void rejectedBeforeTheTruckLeavesHq() throws Exception {
        Booking booking = bookingWithTrip("ready_for_pickup", "\"acks\":{\"driver\":1}");

        HttpResponse<String> response = addToll(booking, driverToken, 185000, "Trạm BOT Long Thành", receipt(booking));

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(tollsOf(booking)).isEmpty();
    }

    @Test
    void rejectedOnceTheTruckIsBackAtHq() throws Exception {
        Booking booking = bookingWithTrip("settlement_issued",
                ("\"departedAt\":%d,\"run\":{\"startedAt\":%d,\"deliveredAt\":%d},"
                        + "\"homeReturn\":{\"fromPlace\":\"Hà Nội\",\"earliestAt\":%d,\"backAt\":%d}")
                        .formatted(epoch(-30), epoch(-28), epoch(-20), epoch(-5), epoch(-1)));

        HttpResponse<String> response = addToll(booking, driverToken, 185000, "Trạm BOT Long Thành", receipt(booking));

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(tollsOf(booking)).isEmpty();
    }

    @Test
    void onlyTheDriverOfTheTripCanRecordIt() throws Exception {
        Booking booking = bookingWithTrip("en_route_to_pickup", "\"departedAt\":%d".formatted(epoch(-2)));

        HttpResponse<String> response = addToll(booking, otherDriverToken, 185000, "Trạm BOT Long Thành", receipt(booking));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
        assertThat(tollsOf(booking)).isEmpty();
    }

    @Test
    void driverCanRemoveAWrongEntry() throws Exception {
        Booking booking = bookingWithTrip("en_route_to_pickup", "\"departedAt\":%d".formatted(epoch(-2)));
        addToll(booking, driverToken, 185000, "Trạm BOT Long Thành", receipt(booking));
        String tollId = tollsOf(booking).path(0).path("id").asText();

        HttpResponse<String> response = tripUpdate(booking, driverToken, "REMOVE_TOLL", "{\"tollId\":\"%s\"}".formatted(tollId));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(tollsOf(booking)).isEmpty();
    }

    @Test
    void theCompanyPaysTheTollSoTheCustomerSettlementStaysAtZero() throws Exception {
        Booking booking = bookingWithTrip("delivered_pending_settlement", deliveredAndDrivingBack());
        addToll(booking, driverToken, 185000, "Trạm BOT Long Thành", receipt(booking));

        HttpResponse<String> submit = post("/settlements/" + booking.getId() + "/submit-expenses", driverToken,
                "{\"tripId\":\"TRP-1\",\"expenses\":[]}");

        assertThat(submit.statusCode()).as(submit.body()).isEqualTo(200);
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("expenses_submitted");
        assertThat(json.readTree(saved.getDetailsJson()).path("settlement").path("total").asLong()).isZero();
    }

    @Test
    void theTripReportShowsWhatTheCompanyPaidInTolls() throws Exception {
        Booking booking = bookingWithTrip("delivered_pending_settlement", deliveredAndDrivingBack());
        addToll(booking, driverToken, 185000, "Trạm BOT Long Thành", receipt(booking));
        addToll(booking, driverToken, 60000, "Trạm Pháp Vân", receipt(booking));

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/trip-reports"))
                .header("Authorization", "Bearer " + managerToken).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode report = null;
        for (JsonNode item : json.readTree(response.body()).path("data")) {
            if (("BK-" + booking.getId()).equals(item.path("id").asText())) report = item;
        }
        assertThat(report).as(response.body()).isNotNull();
        assertThat(report.path("tollCost").asLong()).isEqualTo(245000);
        assertThat(report.path("tolls")).hasSize(2);
    }

    private static long epoch(long hoursFromNow) {
        return Instant.now().plus(hoursFromNow, ChronoUnit.HOURS).toEpochMilli();
    }

    private static String deliveredAndDrivingBack() {
        return ("\"departedAt\":%d,\"run\":{\"startedAt\":%d,\"deliveredAt\":%d},"
                + "\"homeReturn\":{\"fromPlace\":\"Hà Nội\",\"earliestAt\":%d,\"eta\":%d}")
                .formatted(epoch(-12), epoch(-10), epoch(-5), epoch(5), epoch(6));
    }

    // Ảnh hóa đơn đi qua API camera như các chứng từ khác: lưu sẵn bản ghi ảnh của tài xế cho chuyến này
    private String receipt(Booking booking) {
        String url = "https://files.example.com/hoa-don-" + UUID.randomUUID() + ".jpg";
        captures.save(CaptureAsset.builder().url(url).bookingId(booking.getId()).tripId("TRP-1")
                .purpose("TOLL").target("toll").capturedBy(driverUsername).capturedAt(Instant.now()).recordedAt(Instant.now()).build());
        return url;
    }

    private HttpResponse<String> addToll(Booking booking, String token, long amount, String station, String photo) throws Exception {
        String payload = photo == null
                ? "{\"amount\":%d,\"station\":\"%s\"}".formatted(amount, station)
                : "{\"amount\":%d,\"station\":\"%s\",\"photo\":\"%s\"}".formatted(amount, station, photo);
        return tripUpdate(booking, token, "ADD_TOLL", payload);
    }

    private HttpResponse<String> tripUpdate(Booking booking, String token, String action, String payload) throws Exception {
        return post("/field/bookings/" + booking.getId() + "/trip-update", token,
                "{\"tripId\":\"TRP-1\",\"action\":\"%s\",\"actor\":\"Tài xế\",\"payload\":%s}".formatted(action, payload));
    }

    private JsonNode tollsOf(Booking booking) throws Exception {
        String details = bookings.findById(booking.getId()).orElseThrow().getDetailsJson();
        return json.readTree(details).path("trips").path(0).path("tolls");
    }

    private Booking bookingWithTrip(String status, String tripState) {
        return bookings.save(Booking.builder()
                .customerID(customer)
                .status(status)
                .bookingType("domestic")
                .bookingDate(Instant.now().minus(30, ChronoUnit.DAYS))
                .departureDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .pickupAddress("Kho Đồng Nai")
                .dropoffAddress("Hà Nội")
                .detailsJson("{\"trips\":[{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[\"H-1\"],\"driverId\":\"%s\",%s}]}"
                        .formatted(driverCode, tripState))
                .build());
    }

    private User staffAccount(String username, String role, String code) {
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
        return user;
    }

    private String login(User user) throws Exception {
        HttpResponse<String> login = post("/auth/staff/login", null,
                "{\"username\":\"%s\",\"password\":\"Test@123\"}".formatted(user.getEmail()));
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        return json.readTree(login.body()).path("data").path("accessToken").asText();
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
