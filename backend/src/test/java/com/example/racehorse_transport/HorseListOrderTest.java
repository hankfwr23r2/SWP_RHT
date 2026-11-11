package com.example.racehorse_transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Danh sách hồ sơ ngựa của khách: con mới thêm hoặc vừa chỉnh sửa đứng trước
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockTurnstileVerification
class HorseListOrderTest {

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

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void newestOrLastEditedHorseComesFirst() throws Exception {
        String token = registerAndLogin();
        JsonNode first = created(token, "Một");
        Thread.sleep(5);
        created(token, "Hai");
        Thread.sleep(5);
        created(token, "Ba");

        assertThat(names(token)).containsExactly("Ba", "Hai", "Một");

        Thread.sleep(5);
        var edit = (com.fasterxml.jackson.databind.node.ObjectNode) first.deepCopy();
        edit.put("color", "Xám");
        HttpResponse<String> updated = send("PUT", "/horses/" + first.path("id").asInt(), token, json.writeValueAsString(edit));
        assertThat(updated.statusCode()).as(updated.body()).isEqualTo(200);

        assertThat(names(token)).containsExactly("Một", "Ba", "Hai");
    }

    private JsonNode created(String token, String name) throws Exception {
        HttpResponse<String> response = send("POST", "/horses", token, json.writeValueAsString(java.util.Map.of(
                "horseName", name, "microchipID", uniqueChip(), "gender", "MARE", "breed", "Arabian", "age", 5, "weight", 450)));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return json.readTree(response.body()).path("data");
    }

    private List<String> names(String token) throws Exception {
        HttpResponse<String> response = send("GET", "/horses", token, "");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        List<String> names = new ArrayList<>();
        json.readTree(response.body()).path("data").forEach(h -> names.add(h.path("horseName").asText()));
        return names;
    }

    private static String uniqueChip() {
        return "985" + String.format("%012d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1_000_000_000_000L);
    }

    private HttpResponse<String> createHorse(String token, String chip) throws Exception {
        return send("POST", "/horses", token, json.writeValueAsString(java.util.Map.of(
                "horseName", "Storm", "microchipID", chip, "gender", "MARE", "breed", "Arabian", "age", 5, "weight", 450)));
    }

    private String registerAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "khach" + suffix + "@gmail.com";
        HttpResponse<String> register = send("POST", "/auth/register", null, """
                {"username":"khach%s","email":"%s","password":"Test@123","fullName":"Khach Test",
                 "phone":"0900000098","address":"Ha Noi"}""".formatted(suffix, email));
        assertThat(register.statusCode()).as(register.body()).isBetween(200, 201);
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement verify = connection.prepareStatement("UPDATE users SET email_verified = true WHERE email = ?")) {
            verify.setString(1, email);
            assertThat(verify.executeUpdate()).isEqualTo(1);
        }
        HttpResponse<String> login = send("POST", "/auth/customer/login", null,
                "{\"username\":\"%s\",\"password\":\"Test@123\"}".formatted(email));
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        return json.readTree(login.body()).path("data").path("accessToken").asText();
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
