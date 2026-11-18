package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.HorseRepository;
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

// Đơn bị hủy (quản lý hủy chuyến vì khách không trả 70%): từ lúc đó tài xế bấm "Bắt đầu vận chuyển"
// phải nhận thông báo đơn đã hủy, không phải câu "Khách hàng cần thanh toán đủ 70%" như thể đơn còn chạy.
// Áp dụng cả lúc đang trả ngựa (returning_horses) lẫn khi đã hủy hẳn (cancelled), và trước mọi kiểm tra khác (kể cả bảo hiểm).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockTurnstileVerification
class CancelledBookingTripTest {

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
    private HorseRepository horses;
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
    void driverStartingAfterTheManagerCancelledIsToldTheBookingIsCancelledNotThatTheBalanceIsDue() throws Exception {
        Booking booking = booking(oneHorse(), pickup("\"chips\":[\"VN-800221\"],\"originals\":" + ORIGINALS
                + ",\"handoverPhoto\":\"https://example.com/bien-ban.jpg\""));
        payments.save(Payment.builder().bookingID(booking).amount(new BigDecimal("3000000")).paymentDate(Instant.now())
                .paymentType("DEPOSIT").paymentMethod("VNPAY").transactionCode("DEP-" + booking.getId()).status("COMPLETED").build());

        // Đơn còn chạy mà khách chưa trả 70%: câu nhắc 70% là đúng
        HttpResponse<String> whileActive = tripUpdate(booking, driverToken, "START_JOURNEY", "{}");
        assertThat(whileActive.statusCode()).as(whileActive.body()).isBetween(400, 499);
        assertThat(messageOf(whileActive)).contains("70%");

        // Khách không trả 70%, tài xế báo quản lý, quản lý hủy chuyến do khách (ngựa đã lên xe nên chuyển sang trả ngựa)
        HttpResponse<String> cancel = post("/manager/bookings/" + booking.getCode() + "/cancel-trip", managerToken,
                "{\"fault\":\"CUSTOMER\",\"reason\":\"Khách không thanh toán 70%\"}");
        assertThat(cancel.statusCode()).as(cancel.body()).isEqualTo(200);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("returning_horses");

        // Tài xế chưa biết đã hủy và bấm khởi hành
        HttpResponse<String> whileReturning = tripUpdate(booking, driverToken, "START_JOURNEY", "{}");
        assertThat(whileReturning.statusCode()).as(whileReturning.body()).isBetween(400, 499);
        assertThat(messageOf(whileReturning)).contains("hủy").doesNotContain("70%");
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("returning_horses");

        // Đã trả ngựa xong: đơn hủy hẳn, vẫn không khởi hành được và vẫn báo đã hủy
        Booking returned = bookings.findById(booking.getId()).orElseThrow();
        returned.setStatus("cancelled");
        bookings.save(returned);
        HttpResponse<String> afterCancel = tripUpdate(booking, driverToken, "START_JOURNEY", "{}");
        assertThat(afterCancel.statusCode()).as(afterCancel.body()).isBetween(400, 499);
        assertThat(messageOf(afterCancel)).contains("hủy").doesNotContain("70%");
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("cancelled");
    }

    @Test
    void cancelledBookingWithUnconfirmedHorseInsuranceStillSaysItIsCancelled() throws Exception {
        String uninsured = oneHorse().replace("\"status\":\"ELIGIBLE\"}", "\"status\":\"ELIGIBLE\",\"insurance\":{\"option\":\"assistance\"}}");
        Booking booking = booking(uninsured, pickup("\"chips\":[\"VN-800221\"],\"originals\":" + ORIGINALS
                + ",\"handoverPhoto\":\"https://example.com/bien-ban.jpg\""));
        payments.save(Payment.builder().bookingID(booking).amount(new BigDecimal("3000000")).paymentDate(Instant.now())
                .paymentType("DEPOSIT").paymentMethod("VNPAY").transactionCode("DEP-" + booking.getId()).status("COMPLETED").build());

        // Đơn còn chạy: bảo hiểm chưa xác nhận thì xe không khởi hành được, câu báo về bảo hiểm là đúng
        HttpResponse<String> whileActive = tripUpdate(booking, driverToken, "START_JOURNEY", "{}");
        assertThat(whileActive.statusCode()).as(whileActive.body()).isBetween(400, 499);
        assertThat(messageOf(whileActive)).contains("bảo hiểm");

        HttpResponse<String> cancel = post("/manager/bookings/" + booking.getCode() + "/cancel-trip", managerToken,
                "{\"fault\":\"CUSTOMER\",\"reason\":\"Khách không thanh toán 70%\"}");
        assertThat(cancel.statusCode()).as(cancel.body()).isEqualTo(200);

        HttpResponse<String> afterCancel = tripUpdate(booking, driverToken, "START_JOURNEY", "{}");

        assertThat(afterCancel.statusCode()).as(afterCancel.body()).isBetween(400, 499);
        assertThat(messageOf(afterCancel)).contains("hủy").doesNotContain("bảo hiểm");
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
        return post("/field/bookings/" + booking.getCode() + "/trip-update", token,
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
