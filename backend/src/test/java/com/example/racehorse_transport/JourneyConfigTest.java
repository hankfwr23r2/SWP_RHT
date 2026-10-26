package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Admin chỉnh được các quy định dùng để tính lịch trình xe tải (nhóm "Lộ trình & Giờ lái"):
// giờ lái tối đa mỗi ngày, giờ nghỉ đêm, giờ làm thủ tục cửa khẩu, tốc độ tối đa của xe tải gửi cho Valhalla.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JourneyConfigTest {

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
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String adminToken;

    @BeforeEach
    void admin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername("admin" + suffix);
        user.setEmail("admin" + suffix + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("Test@123"));
        user = users.save(user);
        Staff admin = new Staff();
        admin.setUser(user);
        admin.setRole("ADMIN");
        admin.setStaffCode("AD-" + suffix);
        admin.setEmploymentStatus("ACTIVE");
        admin.setHireDate(LocalDate.now());
        staff.save(admin);
        HttpResponse<String> login = send("POST", "/auth/staff/login", null,
                "{\"username\":\"%s\",\"password\":\"Test@123\"}".formatted(user.getEmail()));
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        adminToken = json.readTree(login.body()).path("data").path("accessToken").asText();
    }

    @Test
    void journeyRulesAreListedWithTheirDefaults() throws Exception {
        HttpResponse<String> response = send("GET", "/admin/system-configs/grouped", adminToken, null);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        Map<String, String> values = new HashMap<>();
        for (JsonNode item : json.readTree(response.body()).path("data").path("JOURNEY")) {
            values.put(item.path("configKey").asText(), item.path("configValue").asText());
        }
        assertThat(values).containsExactlyInAnyOrderEntriesOf(Map.of(
                "MAX_DRIVE_HOURS_PER_DAY", "10",
                "OVERNIGHT_REST_HOURS", "10",
                "BORDER_PROCESSING_HOURS", "3",
                "TRUCK_TOP_SPEED_KMH", "80"));
    }

    @Test
    void topSpeedOutsideTheAllowedRangeIsRejected() throws Exception {
        HttpResponse<String> response = send("PUT", "/admin/system-configs/TRUCK_TOP_SPEED_KMH", adminToken,
                "{\"configValue\":\"150\"}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
    }

    @Test
    void adminChangesTheTopSpeed() throws Exception {
        try {
            HttpResponse<String> response = send("PUT", "/admin/system-configs/TRUCK_TOP_SPEED_KMH", adminToken,
                    "{\"configValue\":\"70\"}");

            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            assertThat(json.readTree(response.body()).path("data").path("configValue").asText()).isEqualTo("70");
        } finally {
            // Trả về mặc định để test khác trong lớp (dùng chung DB) vẫn thấy 80
            send("PUT", "/admin/system-configs/TRUCK_TOP_SPEED_KMH", adminToken, "{\"configValue\":\"80\"}");
        }
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
