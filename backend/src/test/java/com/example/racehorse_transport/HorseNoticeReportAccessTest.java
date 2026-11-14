package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Notification;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.NotificationRepository;
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
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Quyền truy cập hồ sơ ngựa, giấy tờ ngựa, thông báo (chuông) và báo cáo chuyến:
// khách chỉ thấy và xóa được của mình, báo cáo chuyến chỉ Quản lý/Quản trị, danh mục giống ngựa chỉ Quản trị sửa được.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockTurnstileVerification
class HorseNoticeReportAccessTest {

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
    private NotificationRepository notifications;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String managerToken;
    private String ownerToken;
    private String strangerToken;
    private Customer owner;

    @BeforeEach
    void accounts() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        managerToken = staffLogin("quanly" + suffix, "MANAGER", "MG-" + suffix);
        owner = customer("chu" + suffix, "0912345678");
        ownerToken = customerLogin(owner);
        strangerToken = customerLogin(customer("la" + suffix, "0900000000"));
    }

    // ===== Giấy tờ ngựa =====

    @Test
    void onlyTheOwnerSeesTheDocumentsOfAHorse() throws Exception {
        int horseId = createHorse(ownerToken);

        assertThat(get("/horse-documents/horse/" + horseId, ownerToken).statusCode()).isEqualTo(200);
        assertThat(get("/horse-documents/horse/" + horseId, strangerToken).statusCode()).isEqualTo(403);
        assertThat(get("/horse-documents/horse/" + horseId, managerToken).statusCode()).isEqualTo(200);
    }

    @Test
    void aCustomerCannotViewOrDeleteSomeoneElsesHorse() throws Exception {
        int horseId = createHorse(ownerToken);

        assertThat(delete("/horse-documents/999999", ownerToken).statusCode()).isEqualTo(404);
        assertThat(get("/horses/" + horseId, strangerToken).statusCode()).isEqualTo(403);
        assertThat(delete("/horses/" + horseId, strangerToken).statusCode()).isEqualTo(403);
        assertThat(get("/horses/" + horseId, ownerToken).statusCode()).isEqualTo(200);
    }

    // ===== Thông báo =====

    @Test
    void aUserDeletesOnlyTheirOwnNotice() throws Exception {
        Notification mine = notice(owner.getUser().getUsername());
        Notification theirs = notice("nguoi-khac");

        assertThat(delete("/notifications/" + theirs.getId() + "?role=customer", ownerToken).statusCode()).isEqualTo(404);
        assertThat(notifications.existsById(theirs.getId())).isTrue();
        assertThat(delete("/notifications/" + mine.getId() + "?role=customer", ownerToken).statusCode()).isEqualTo(200);
        assertThat(notifications.existsById(mine.getId())).isFalse();
    }

    @Test
    void noticesAreListedOnlyForTheCallersOwnRole() throws Exception {
        notice(owner.getUser().getUsername());
        notice("nguoi-khac");

        HttpResponse<String> response = get("/notifications?role=customer", ownerToken);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode data = json.readTree(response.body()).path("data");
        assertThat(data).hasSize(1);
        assertThat(data.get(0).path("name").asText()).isEqualTo(owner.getUser().getUsername());
        assertThat(get("/notifications?role=manager", ownerToken).statusCode()).isEqualTo(403);
        assertThat(get("/notifications?role=customer", null).statusCode()).isIn(401, 403);
    }

    @Test
    void markAllAsReadOnlyTouchesTheCallersNotices() throws Exception {
        Notification mine = notice(owner.getUser().getUsername());
        Notification theirs = notice("nguoi-khac");

        HttpResponse<String> response = patch("/notifications/read-all?role=customer", ownerToken);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(notifications.findById(mine.getId()).orElseThrow().getIsRead()).isTrue();
        assertThat(notifications.findById(theirs.getId()).orElseThrow().getIsRead()).isNotEqualTo(true);
    }

    // ===== Báo cáo chuyến và giống ngựa =====

    @Test
    void tripReportsAreForManagersOnly() throws Exception {
        assertThat(get("/trip-reports", managerToken).statusCode()).isEqualTo(200);
        assertThat(get("/trip-reports", ownerToken).statusCode()).isEqualTo(403);
        assertThat(get("/trip-reports", null).statusCode()).isIn(401, 403);
    }

    @Test
    void horseBreedsCanBeReadByCustomersButChangedOnlyByAdmins() throws Exception {
        assertThat(get("/horse-types", ownerToken).statusCode()).isEqualTo(200);
        assertThat(post("/horse-types", ownerToken, "{\"typeName\":\"Giong la\",\"insuredValue\":1000000}").statusCode()).isEqualTo(403);
        assertThat(post("/horse-types", managerToken, "{\"typeName\":\"Giong la\",\"insuredValue\":1000000}").statusCode()).isEqualTo(403);
        assertThat(delete("/horse-types/1", ownerToken).statusCode()).isEqualTo(403);
    }

    // ===== Dựng dữ liệu =====

    private int createHorse(String token) throws Exception {
        String chip = "98" + "%013d".formatted(System.nanoTime() % 10_000_000_000_000L);
        HttpResponse<String> response = post("/horses", token, json.writeValueAsString(Map.of(
                "horseName", "Storm", "microchipID", chip, "gender", "MARE", "breed", "Arabian", "age", 5, "weight", 450)));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json.readTree(response.body()).path("data").path("id").asInt();
    }

    private Notification notice(String username) {
        return notifications.save(Notification.builder().recipientRole("customer").recipientUsername(username)
                .title("Thông báo thử").message("Nội dung").isRead(false).createdAt(java.time.Instant.now()).build());
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

    private HttpResponse<String> delete(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path)).DELETE();
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .method("PATCH", HttpRequest.BodyPublishers.noBody());
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
