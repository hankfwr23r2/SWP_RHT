package com.example.racehorse_transport;

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
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Mã microchip theo chuẩn ISO 11784/11785: đúng 15 chữ số. Khách gõ có dấu cách/gạch nối vẫn được,
// hệ thống bỏ đi trước khi kiểm tra trùng và lưu.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockTurnstileVerification
class HorseMicrochipTest {

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
    void chipThatIsNotFifteenDigitsIsRejected() throws Exception {
        String token = registerAndLogin();

        for (String chip : List.of("VN-985211", "98511200012345", "9851120001234567", "98511200012345A")) {
            HttpResponse<String> response = createHorse(token, chip);

            assertThat(response.statusCode()).as(chip + " → " + response.body()).isEqualTo(400);
            assertThat(json.readTree(response.body()).path("message").asText()).contains("15 chữ số");
        }
    }

    @Test
    void spacesAndHyphensAreRemovedBeforeSaving() throws Exception {
        String token = registerAndLogin();
        String digits = uniqueChip();
        String typed = " " + digits.substring(0, 3) + " " + digits.substring(3, 6) + "-" + digits.substring(6) + " ";

        HttpResponse<String> response = createHorse(token, typed);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        assertThat(json.readTree(response.body()).path("data").path("microchipID").asText()).isEqualTo(digits);
    }

    @Test
    void sameChipTypedDifferentlyIsStillADuplicate() throws Exception {
        String token = registerAndLogin();
        String digits = uniqueChip();
        assertThat(createHorse(token, digits).statusCode()).isEqualTo(201);

        HttpResponse<String> again = createHorse(token, digits.substring(0, 3) + " " + digits.substring(3));

        assertThat(again.statusCode()).as(again.body()).isEqualTo(400);
        assertThat(json.readTree(again.body()).path("message").asText()).contains("đã được sử dụng");
    }

    @Test
    void databaseItselfRefusesASecondHorseWithTheSameChip() throws Exception {
        String token = registerAndLogin();
        String digits = uniqueChip();
        int horseId = json.readTree(createHorse(token, digits).body()).path("data").path("id").asInt();

        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement copy = connection.prepareStatement(
                     "insert into horse (horse_name, microchipid, customerid, completed_trips) select 'Copy', microchipid, customerid, 0 from horse where horseid = ?")) {
            copy.setInt(1, horseId);
            assertThatThrownBy(copy::executeUpdate).isInstanceOf(SQLException.class).hasMessageContaining("duplicate key");
        }
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
