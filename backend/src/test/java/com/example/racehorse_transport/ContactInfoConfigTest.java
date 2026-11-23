package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
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

import java.math.BigDecimal;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Hotline và địa chỉ văn phòng không còn là số mẫu cứng trong mã: admin cấu hình ở trang tham số hệ thống,
// khách thấy giá trị đang cấu hình (qua /public/configs) và thông báo của backend nhắc đúng số hotline đó.
// Giá trị ban đầu vẫn là thông tin mẫu cho tới khi admin nhập.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockTurnstileVerification
class ContactInfoConfigTest {

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
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String adminToken;

    @BeforeEach
    void admin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        adminToken = login("quantri" + suffix, "ADMIN", "AD-" + suffix);
    }

    @Test
    void publicConfigsExposeHotlineAndOfficeAddressAsContactSettings() throws Exception {
        HttpResponse<String> response = get("/public/configs", null);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode hotline = configOf(response, "HOTLINE");
        JsonNode address = configOf(response, "OFFICE_ADDRESS");
        assertThat(hotline.path("configGroup").asText()).isEqualTo("CONTACT");
        assertThat(address.path("configGroup").asText()).isEqualTo("CONTACT");
        assertThat(hotline.path("configValue").asText()).isNotBlank();
        assertThat(address.path("configValue").asText()).isNotBlank();
    }

    @Test
    void adminChangesTheHotlineAndTheAddressAndTheyShowUpPublicly() throws Exception {
        HttpResponse<String> hotline = put("/admin/system-configs/HOTLINE", adminToken, "{\"configValue\":\"1900 1234\"}");
        HttpResponse<String> address = put("/admin/system-configs/OFFICE_ADDRESS", adminToken,
                "{\"configValue\":\"25 Nguyễn Huệ, Quận 1, TP.HCM\"}");

        assertThat(hotline.statusCode()).as(hotline.body()).isEqualTo(200);
        assertThat(address.statusCode()).as(address.body()).isEqualTo(200);
        HttpResponse<String> publicConfigs = get("/public/configs", null);
        assertThat(configOf(publicConfigs, "HOTLINE").path("configValue").asText()).isEqualTo("1900 1234");
        assertThat(configOf(publicConfigs, "OFFICE_ADDRESS").path("configValue").asText()).isEqualTo("25 Nguyễn Huệ, Quận 1, TP.HCM");
        assertThat(configOf(publicConfigs, "HOTLINE").path("updatedAt").isMissingNode()).isFalse();
    }

    @Test
    void invalidHotlineAndAddressAreRejectedAndTheOldValueStays() throws Exception {
        String hotlineBefore = configOf(get("/public/configs", null), "HOTLINE").path("configValue").asText();
        String addressBefore = configOf(get("/public/configs", null), "OFFICE_ADDRESS").path("configValue").asText();

        for (String bad : new String[] { "gọi cho tôi", "1900 6868 6868 6868 6868 6868 6868", "<b>19006868</b>" }) {
            HttpResponse<String> response = put("/admin/system-configs/HOTLINE", adminToken,
                    json.createObjectNode().put("configValue", bad).toString());
            assertThat(response.statusCode()).as("hotline " + bad + ": " + response.body()).isBetween(400, 499);
        }
        for (String bad : new String[] { "abc", "x".repeat(201) }) {
            HttpResponse<String> response = put("/admin/system-configs/OFFICE_ADDRESS", adminToken,
                    json.createObjectNode().put("configValue", bad).toString());
            assertThat(response.statusCode()).as("địa chỉ " + bad.length() + " ký tự: " + response.body()).isBetween(400, 499);
        }

        HttpResponse<String> after = get("/public/configs", null);
        assertThat(configOf(after, "HOTLINE").path("configValue").asText()).isEqualTo(hotlineBefore);
        assertThat(configOf(after, "OFFICE_ADDRESS").path("configValue").asText()).isEqualTo(addressBefore);
    }

    @Test
    void numericConfigsStillRejectText() throws Exception {
        HttpResponse<String> response = put("/admin/system-configs/DEPOSIT_RATE", adminToken, "{\"configValue\":\"1900 6868\"}");

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
    }

    @Test
    void customerWhoCannotCancelAPaidBookingIsToldTheConfiguredHotline() throws Exception {
        assertThat(put("/admin/system-configs/HOTLINE", adminToken, "{\"configValue\":\"1900 7777\"}").statusCode()).isEqualTo(200);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User customerUser = new User();
        customerUser.setUsername("khach" + suffix);
        customerUser.setEmail("khach" + suffix + "@example.com");
        customerUser.setPasswordHash(passwordEncoder.encode("Test@123"));
        customerUser.setEmailVerified(true);
        customerUser = users.save(customerUser);
        Customer customer = new Customer();
        customer.setUser(customerUser);
        customer.setFullName("Khach Test");
        customer = customers.save(customer);
        Booking booking = bookings.save(Booking.builder().customerID(customer).status("en_route_to_pickup").bookingType("domestic")
                .bookingDate(Instant.now().minus(25, ChronoUnit.DAYS)).departureDate(Instant.now()).pickupAddress("Kho Đồng Nai")
                .dropoffAddress("Hà Nội").detailsJson("{\"quote\":{\"total\":10000000},\"payment\":{\"depositPaid\":true,\"balancePaid\":true}}").build());
        for (String type : new String[] { "DEPOSIT", "BALANCE" }) {
            payments.save(Payment.builder().bookingID(booking).amount(new BigDecimal("5000000")).paymentDate(Instant.now())
                    .paymentType(type).paymentMethod("VNPAY").transactionCode(type + "-" + booking.getId()).status("COMPLETED").build());
        }
        String token = json.readTree(post("/auth/customer/login", null,
                "{\"username\":\"%s\",\"password\":\"Test@123\"}".formatted(customerUser.getEmail())).body()).path("data").path("accessToken").asText();

        HttpResponse<String> response = post("/payments/cancel?bookingId=" + booking.getCode(), token, "{}");

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(json.readTree(response.body()).path("message").asText()).contains("1900 7777");
    }

    private JsonNode configOf(HttpResponse<String> response, String key) throws Exception {
        for (JsonNode config : json.readTree(response.body()).path("data"))
            if (key.equals(config.path("configKey").asText())) return config;
        throw new AssertionError("Không thấy cấu hình " + key + ": " + response.body());
    }

    private String login(String username, String role, String code) throws Exception {
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

    private HttpResponse<String> put(String path, String token, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
