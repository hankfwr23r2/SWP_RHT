package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.service.VnpayService;
import com.example.racehorse_transport.util.VnpayUtil;
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

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Các vai trò nội bộ nhận thông báo (chuông) khi có việc tới lượt mình, không chỉ khách hàng:
// Chuyên viên và Điều phối viên được giao đơn, bị Quản lý trả lại (kèm lý do riêng); Quản lý khi đơn đủ điều kiện duyệt giá;
// Tài xế và Hộ tống của chuyến khi khách đã trả 70% (xe được xuất phát). Mỗi người chỉ thấy thông báo của mình.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "vnpay.hash-secret=NOTICE_TEST_SECRET")
@MockTurnstileVerification
class StaffNotificationTest {

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
    private PaymentRepository payments;
    @Autowired
    private VnpayService vnpayService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String suffix;
    private String managerToken;
    private String specialistToken;
    private String otherSpecialistToken;
    private String coordinatorToken;
    private String driverToken;
    private String escortToken;
    private String specialistCode;
    private String coordinatorCode;
    private String driverCode;
    private String escortCode;
    private Customer customer;
    private String customerToken;

    @BeforeEach
    void team() throws Exception {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        managerToken = login(staffAccount("quanly" + suffix, "MANAGER", "MG-" + suffix));
        specialistCode = "SP-" + suffix;
        specialistToken = login(staffAccount("chuyenvien" + suffix, "SPECIALIST", specialistCode));
        otherSpecialistToken = login(staffAccount("chuyenvienkhac" + suffix, "SPECIALIST", "SP2-" + suffix));
        coordinatorCode = "CO-" + suffix;
        coordinatorToken = login(staffAccount("dieuphoi" + suffix, "COORDINATOR", coordinatorCode));
        driverCode = "DR-" + suffix;
        driverToken = login(staffAccount("taixe" + suffix, "DRIVER", driverCode));
        escortCode = "ES-" + suffix;
        escortToken = login(staffAccount("hotong" + suffix, "ESCORT", escortCode));

        User customerUser = new User();
        customerUser.setUsername("khach" + suffix);
        customerUser.setEmail("khach" + suffix + "@gmail.com");
        customerUser.setPasswordHash(passwordEncoder.encode("Test@123"));
        customerUser.setEmailVerified(true);
        customerUser = users.save(customerUser);
        customer = new Customer();
        customer.setUser(customerUser);
        customer.setFullName("Khach Test");
        customer = customers.save(customer);
        HttpResponse<String> login = post("/auth/customer/login", null,
                "{\"username\":\"%s\",\"password\":\"Test@123\"}".formatted(customerUser.getEmail()));
        customerToken = login.statusCode() == 200 ? json.readTree(login.body()).path("data").path("accessToken").asText() : null;
    }

    @Test
    void acceptingABookingTellsTheAssignedSpecialistAndCoordinator() throws Exception {
        Booking booking = booking("pending_intake", "{}");

        HttpResponse<String> response = post("/manager/bookings/" + booking.getId() + "/activate", managerToken,
                ("{\"by\":\"Quản lý\",\"specialist\":{\"id\":\"%s\",\"name\":\"Chuyên viên\"},"
                        + "\"coordinator\":{\"id\":\"%s\",\"name\":\"Điều phối\"}}").formatted(specialistCode, coordinatorCode));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(noticesAbout(booking, "specialist", specialistToken)).isNotEmpty();
        assertThat(noticesAbout(booking, "coordinator", coordinatorToken)).isNotEmpty();
        assertThat(noticesAbout(booking, "specialist", otherSpecialistToken)).isEmpty();
    }

    @Test
    void sendingBackTellsEachSideItsOwnReason() throws Exception {
        Booking booking = booking("pending_commercial", assignedAndReady());

        HttpResponse<String> response = post("/manager/bookings/" + booking.getId() + "/send-back", managerToken,
                "{\"to\":\"both\",\"specialistReason\":\"Hộ chiếu mờ\",\"coordinatorReason\":\"Đổi sang xe Medium\"}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(texts(noticesAbout(booking, "specialist", specialistToken))).anyMatch(text -> text.contains("Hộ chiếu mờ"));
        assertThat(texts(noticesAbout(booking, "coordinator", coordinatorToken))).anyMatch(text -> text.contains("Đổi sang xe Medium"));
        assertThat(texts(noticesAbout(booking, "specialist", specialistToken))).noneMatch(text -> text.contains("Đổi sang xe Medium"));
    }

    @Test
    void managerHearsWhenABookingIsReadyForTheQuote() throws Exception {
        String details = ("{\"intake\":{\"at\":1,\"by\":\"Quản lý\",\"specialist\":{\"id\":\"%s\",\"name\":\"Chuyên viên\"},"
                + "\"coordinator\":{\"id\":\"%s\",\"name\":\"Điều phối\"}},"
                + "\"trips\":[{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[\"H-1\"]}],"
                + "\"plan\":{\"at\":1,\"by\":\"Điều phối\",\"note\":\"\"}}").formatted(specialistCode, coordinatorCode);
        Booking booking = booking("under_review", details);

        HttpResponse<String> response = post("/specialist/bookings/" + booking.getId() + "/verify-horse", specialistToken, "{\"note\":\"Đủ\"}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(noticesAbout(booking, "manager", managerToken)).isNotEmpty();
    }

    @Test
    void crewHearsThatTheBalanceIsPaidAndTheTruckMayLeave() throws Exception {
        String details = ("{\"trips\":[{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[\"H-1\"],"
                + "\"driverId\":\"%s\",\"escortId\":\"%s\",\"departedAt\":%d}],\"payment\":{\"depositPaid\":true}}")
                .formatted(driverCode, escortCode, Instant.now().minus(3, ChronoUnit.HOURS).toEpochMilli());
        Booking booking = booking("en_route_to_pickup", details);
        String txnRef = "BAL-" + UUID.randomUUID();
        payments.save(Payment.builder().bookingID(booking).amount(new BigDecimal("7000000")).paymentType("BALANCE")
                .paymentMethod("VNPAY").status("PENDING").paymentDate(Instant.now()).transactionCode(txnRef).build());

        var result = vnpayService.confirm(paid(txnRef, "700000000"));

        assertThat(result.isSuccess()).as(result.getMessage()).isTrue();
        assertThat(noticesAbout(booking, "driver", driverToken)).isNotEmpty();
        assertThat(noticesAbout(booking, "escort", escortToken)).isNotEmpty();
    }

    @Test
    void noMadeUpNoticesAppearOnAFreshSystem() throws Exception {
        HttpResponse<String> response = get("/notifications?role=driver", driverToken);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("Lệnh điều xe mới").doesNotContain("Đồng Nai đi Long An");
    }

    @Test
    void aCustomerCannotReadTheManagersNotices() throws Exception {
        assertThat(customerToken).as("đăng nhập tài khoản khách để thử").isNotNull();

        HttpResponse<String> response = get("/notifications?role=manager", customerToken);

        assertThat(response.statusCode()).isEqualTo(403);
    }

    private String assignedAndReady() {
        return ("{\"intake\":{\"at\":1,\"by\":\"Quản lý\",\"specialist\":{\"id\":\"%s\",\"name\":\"Chuyên viên\"},"
                + "\"coordinator\":{\"id\":\"%s\",\"name\":\"Điều phối\"}},\"medical\":{\"status\":\"approved\"},"
                + "\"trips\":[{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[\"H-1\"]}],"
                + "\"plan\":{\"at\":1,\"by\":\"Điều phối\",\"note\":\"\"}}").formatted(specialistCode, coordinatorCode);
    }

    private Map<String, String> paid(String txnRef, String amount) {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_Amount", amount);
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "987654321");
        params.put("vnp_PayDate", DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(Instant.now()));
        params.put(VnpayUtil.SECURE_HASH, VnpayUtil.sign(params, "NOTICE_TEST_SECRET"));
        return params;
    }

    private List<JsonNode> noticesAbout(Booking booking, String role, String token) throws Exception {
        HttpResponse<String> response = get("/notifications?role=" + role, token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        List<JsonNode> mine = new ArrayList<>();
        for (JsonNode notice : json.readTree(response.body()).path("data")) {
            if (("BK-" + booking.getId()).equals(notice.path("bookingId").asText())) mine.add(notice);
        }
        return mine;
    }

    private static List<String> texts(List<JsonNode> notices) {
        return notices.stream().map(n -> n.path("title").asText() + " " + n.path("text").asText()).toList();
    }

    private Booking booking(String status, String details) {
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

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Authorization", "Bearer " + token).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
