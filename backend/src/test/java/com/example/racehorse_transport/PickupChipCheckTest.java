package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.NotificationRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
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

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Nhận ngựa ở điểm đón:
// - Hộ tống quét chip: số không thuộc ngựa nào trên chuyến thì bị từ chối ngay, không được tính là đã quét.
// - 70% còn lại chỉ thanh toán được khi kíp xe đã làm xong mọi thủ tục nhận ngựa (chip khớp, bản gốc, biên bản).
// - Ngựa thật sự không khớp hồ sơ: tài xế báo "Chip không khớp hồ sơ", con đó bị loại khỏi chuyến, không giảm trừ tiền.
//   Loại hết ngựa thì đơn sang "Không nhận được ngựa" (pickup_failed), xe không được chạy rỗng.
// - Quản lý hủy đơn "Không nhận được ngựa": khách mất phần cọc vận chuyển, xe và kíp được nhả.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PickupChipCheckTest {

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

    private static final String ORIGINALS = "[\"Hộ chiếu ngựa bản gốc (FEI / National Passport)\",\"Sổ tiêm phòng\","
            + "\"Phiếu xét nghiệm EIA/EVA, bản gốc kèm 02 bản sao công chứng\"]";

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
    private NotificationRepository notifications;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String driverCode;
    private String escortCode;
    private String driverToken;
    private String escortToken;
    private String managerToken;
    private Customer customer;

    @BeforeEach
    void crewManagerAndCustomer() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        driverCode = "DR-" + suffix;
        escortCode = "ES-" + suffix;
        driverToken = staffLogin("taixe" + suffix, "DRIVER", driverCode);
        escortToken = staffLogin("hotong" + suffix, "ESCORT", escortCode);
        managerToken = staffLogin("quanly" + suffix, "MANAGER", "MG-" + suffix);

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
    void chipThatBelongsToNoHorseOnTheTripIsRejectedAndNotCounted() throws Exception {
        Booking booking = booking(oneHorse(), pickup("\"chips\":[]"));

        HttpResponse<String> response = tripUpdate(booking, escortToken, "SCAN_CHIP", "{\"chip\":\"VN-000000\"}");

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(messageOf(response)).contains("VN-000000").contains("không khớp");
        assertThat(pickupOf(booking).path("chips")).isEmpty();
    }

    @Test
    void chipOfAHorseOnTheTripIsRecordedWhateverTheCase() throws Exception {
        Booking booking = booking(oneHorse(), pickup("\"chips\":[]"));

        HttpResponse<String> response = tripUpdate(booking, escortToken, "SCAN_CHIP", "{\"chip\":\" vn-800221 \"}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(pickupOf(booking).path("chips").toString()).isEqualTo("[\"VN-800221\"]");
    }

    @Test
    void balanceIsNotPayableWhileThePickupIsStillInProgress() throws Exception {
        Booking booking = booking(oneHorse(), pickup("\"chips\":[\"VN-800221\"]"));

        HttpResponse<String> response = tripUpdate(booking, driverToken, "COLLECT_ORIGINALS", "{\"items\":" + ORIGINALS + "}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(json.readTree(response.body()).path("data").path("canPayBalance").asBoolean()).isFalse();
    }

    @Test
    void balanceBecomesPayableOnceEveryPickupStepIsDone() throws Exception {
        Booking booking = booking(oneHorse(), pickup("\"chips\":[],\"originals\":" + ORIGINALS
                + ",\"handoverPhoto\":\"https://example.com/bien-ban.jpg\""));

        // Bước cuối cùng: hộ tống quét đúng chip của con ngựa duy nhất
        HttpResponse<String> response = tripUpdate(booking, escortToken, "SCAN_CHIP", "{\"chip\":\"VN-800221\"}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(json.readTree(response.body()).path("data").path("canPayBalance").asBoolean()).isTrue();
        // Khách được nhắc thanh toán ngay lúc đó
        assertThat(notifications.findForUser("customer", customer.getUser().getUsername()))
                .anySatisfy(n -> assertThat(n.getTitle()).contains("Thanh toán 70% còn lại"));
    }

    @Test
    void escortCannotReportAChipMismatch() throws Exception {
        Booking booking = booking(oneHorse(), pickup("\"chips\":[]"));

        HttpResponse<String> response = tripUpdate(booking, escortToken, "CHIP_MISMATCH",
                "{\"horseId\":\"H-1\",\"chipRead\":\"VN-123456\",\"note\":\"Khách đưa ngựa khác\"}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(403);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("en_route_to_pickup");
    }

    @Test
    void mismatchedHorseLeavesTheTripWithoutDiscountAndTheOthersStillGo() throws Exception {
        Booking booking = booking(twoHorses(), pickup("\"chips\":[\"VN-800222\"]"));

        HttpResponse<String> response = tripUpdate(booking, driverToken, "CHIP_MISMATCH",
                "{\"horseId\":\"H-1\",\"chipRead\":\"VN-123456\",\"note\":\"Khách đưa ngựa khác\"}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("en_route_to_pickup");
        JsonNode details = json.readTree(saved.getDetailsJson());
        assertThat(details.path("trips").path(0).path("horseIds").toString()).isEqualTo("[\"H-2\"]");
        JsonNode horse = details.path("horses").path(0);
        assertThat(horse.path("status").asText()).isEqualTo("CHIP_MISMATCH");
        assertThat(horse.path("unfitInfo").path("chipRead").asText()).isEqualTo("VN-123456");
        assertThat(details.path("quote").path("total").asDouble()).isEqualTo(10_000_000d);
    }

    @Test
    void lastMismatchedHorseMovesTheBookingToPickupFailedAndAlertsManagers() throws Exception {
        Booking booking = booking(oneHorse(), pickup("\"chips\":[]"));

        HttpResponse<String> response = tripUpdate(booking, driverToken, "CHIP_MISMATCH",
                "{\"horseId\":\"H-1\",\"chipRead\":\"VN-123456\",\"note\":\"Khách đưa ngựa khác\"}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("pickup_failed");
        HttpResponse<String> notifications = get("/notifications?role=manager", managerToken);
        assertThat(notifications.body()).contains("BK-%03d".formatted(booking.getId())).contains("Không nhận được ngựa");
    }

    @Test
    void truckCannotStartTheJourneyWithoutAnyHorse() throws Exception {
        Booking booking = booking(oneHorse().replace("\"horseIds\":[\"H-1\"]", "\"horseIds\":[]"),
                pickup("\"chips\":[],\"originals\":" + ORIGINALS + ",\"handoverPhoto\":\"https://example.com/bien-ban.jpg\""),
                true);

        HttpResponse<String> response = tripUpdate(booking, driverToken, "START_JOURNEY", "{}");

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(messageOf(response)).contains("không còn ngựa");
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("en_route_to_pickup");
    }

    @Test
    void managerCancelsAPickupFailedBookingAndTheCustomerKeepsNoDepositBack() throws Exception {
        Booking booking = booking(oneHorse(), pickup("\"chips\":[]"));
        booking.setStatus("pickup_failed");
        bookings.save(booking);
        payments.save(Payment.builder().bookingID(booking).amount(new BigDecimal("3000000")).paymentDate(Instant.now())
                .paymentType("DEPOSIT").paymentMethod("VNPAY").transactionCode("DEP-" + booking.getId()).status("COMPLETED").build());

        HttpResponse<String> response = post("/manager/bookings/" + booking.getId() + "/cancel-pickup-failed", managerToken,
                "{\"reason\":\"Chip ngựa không khớp hồ sơ\"}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("cancelled");
        List<Payment> saved = payments.findByBookingID_IdOrderByPaymentDateDesc(booking.getId());
        assertThat(saved).anySatisfy(p -> {
            assertThat(p.getPaymentType()).isEqualTo("PENALTY");
            assertThat(p.getAmount()).isEqualByComparingTo("3000000");
        });
        assertThat(saved).noneMatch(p -> "REFUND".equals(p.getPaymentType()));
    }

    @Test
    void managerCannotCancelABookingThatStillHasHorsesToPickUp() throws Exception {
        Booking booking = booking(oneHorse(), pickup("\"chips\":[]"));

        HttpResponse<String> response = post("/manager/bookings/" + booking.getId() + "/cancel-pickup-failed", managerToken,
                "{\"reason\":\"Thử hủy\"}");

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("en_route_to_pickup");
    }

    private String oneHorse() {
        return "\"horses\":[{\"horseId\":\"H-1\",\"name\":\"Gió Mekong\",\"microchip\":\"VN-800221\",\"status\":\"ELIGIBLE\"}],"
                + "\"trips\":[{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[\"H-1\"],"
                + "\"driverId\":\"%s\",\"escortId\":\"%s\",\"departedAt\":%d,\"run\":{\"checkpoints\":[%%s]}}]";
    }

    private String twoHorses() {
        return "\"horses\":[{\"horseId\":\"H-1\",\"name\":\"Gió Mekong\",\"microchip\":\"VN-800221\",\"status\":\"ELIGIBLE\"},"
                + "{\"horseId\":\"H-2\",\"name\":\"Sao Mai\",\"microchip\":\"VN-800222\",\"status\":\"ELIGIBLE\"}],"
                + "\"trips\":[{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[\"H-1\",\"H-2\"],"
                + "\"driverId\":\"%s\",\"escortId\":\"%s\",\"departedAt\":%d,\"run\":{\"checkpoints\":[%%s]}}]";
    }

    // Xe đã tới điểm đón và chụp ảnh tại chỗ; phần còn lại của thủ tục nhận ngựa do từng test quy định
    private String pickup(String rest) {
        return "{\"id\":\"pickup\",\"type\":\"pickup\",\"label\":\"Điểm đón\",\"place\":\"Kho Đồng Nai\",\"arrivedAt\":%d,"
                .formatted(Instant.now().toEpochMilli())
                + "\"photo\":\"https://example.com/diem-don.jpg\"," + rest + "}";
    }

    private Booking booking(String horsesAndTrips, String pickupCheckpoint) {
        return booking(horsesAndTrips, pickupCheckpoint, false);
    }

    private Booking booking(String horsesAndTrips, String pickupCheckpoint, boolean balancePaid) {
        long now = Instant.now().toEpochMilli();
        String body = horsesAndTrips.formatted(driverCode, escortCode, now).formatted(pickupCheckpoint);
        return bookings.save(Booking.builder()
                .customerID(customer)
                .status("en_route_to_pickup")
                .bookingType("domestic")
                .bookingDate(Instant.now().minus(25, ChronoUnit.DAYS))
                .departureDate(Instant.now())
                .pickupAddress("Kho Đồng Nai")
                .dropoffAddress("Hà Nội")
                .waybillNo("VD-TEST")
                .detailsJson("{" + body + ",\"quote\":{\"total\":10000000,\"deposit\":3000000,\"balance\":7000000},"
                        + "\"payment\":{\"depositPaid\":true,\"balancePaid\":" + balancePaid + "}}")
                .build());
    }

    private JsonNode pickupOf(Booking booking) throws Exception {
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        return json.readTree(saved.getDetailsJson()).path("trips").path(0).path("run").path("checkpoints").path(0);
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

    private HttpResponse<String> tripUpdate(Booking booking, String token, String action, String payload) throws Exception {
        return post("/field/bookings/" + booking.getId() + "/trip-update", token,
                "{\"tripId\":\"TRP-1\",\"action\":\"%s\",\"actor\":\"Kíp xe\",\"payload\":%s}".formatted(action, payload));
    }

    private String messageOf(HttpResponse<String> response) throws Exception {
        return json.readTree(response.body()).path("message").asText();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
