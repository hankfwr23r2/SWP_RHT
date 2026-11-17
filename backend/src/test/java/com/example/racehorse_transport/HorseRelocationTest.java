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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Chủ ngựa tự cập nhật vị trí chuồng. Ngựa đang nằm trong đơn chưa xong thì vị trí do chuyến đi quyết định,
// chủ ngựa không tự đổi được; trạng thái vận chuyển chỉ hệ thống đặt.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockTurnstileVerification
class HorseRelocationTest {

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
    void ownerRelocatesIdleHorseAndNoteIsKeptInJourneyBook() throws Exception {
        String token = registerAndLogin();
        int horseId = createHorse(token);

        HttpResponse<String> response = relocate(token, horseId,
                "Trại Hòa Bình, Xã Long Phước, Tỉnh Đồng Nai", "Liên hệ anh Tâm 0900000011");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode horse = json.readTree(response.body()).path("data");
        assertThat(horse.path("currentLocationName").asText()).isEqualTo("Trại Hòa Bình, Xã Long Phước, Tỉnh Đồng Nai");
        JsonNode log = json.readTree(get("/horses/" + horseId + "/journey-logs", token).body()).path("data").get(0);
        assertThat(log.path("source").asText()).isEqualTo("OWNER_RELOCATION");
        assertThat(log.path("notes").asText()).contains("Liên hệ anh Tâm 0900000011");
    }

    @Test
    void horseInUnfinishedBookingCannotBeRelocated() throws Exception {
        String token = registerAndLogin();
        int horseId = createHorse(token);
        insertDocuments(horseId, List.of("PASSPORT", "VACCINATION", "BLOOD_TEST"));
        HttpResponse<String> booking = createBooking(token, horseId);
        assertThat(booking.statusCode()).as(booking.body()).isEqualTo(201);
        setTransitStatus(horseId, "IN_TRANSIT");

        HttpResponse<String> response = relocate(token, horseId, "Trại khác, Xã Long Phước, Tỉnh Đồng Nai", null);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        JsonNode horse = json.readTree(get("/horses/" + horseId, token).body()).path("data");
        assertThat(horse.path("transitStatus").asText()).isEqualTo("IN_TRANSIT");
        assertThat(horse.path("currentLocationName").asText()).isNotEqualTo("Trại khác, Xã Long Phước, Tỉnh Đồng Nai");
        JsonNode logs = json.readTree(get("/horses/" + horseId + "/journey-logs", token).body()).path("data");
        assertThat(logs.size()).isZero();
    }

    @Test
    void relocatingToTheSameAddressIsRejected() throws Exception {
        String token = registerAndLogin();
        int horseId = createHorse(token);
        assertThat(relocate(token, horseId, "Trại Hòa Bình, Xã Long Phước, Tỉnh Đồng Nai", null).statusCode()).isEqualTo(200);

        HttpResponse<String> again = relocate(token, horseId, "  Trại Hòa Bình, Xã Long Phước, Tỉnh Đồng Nai ", null);

        assertThat(again.statusCode()).as(again.body()).isEqualTo(400);
        JsonNode logs = json.readTree(get("/horses/" + horseId + "/journey-logs", token).body()).path("data");
        assertThat(logs.size()).isEqualTo(1);
    }

    @Test
    void ownerCannotChangeLocationOrTransitStatusThroughProfileEdit() throws Exception {
        String token = registerAndLogin();
        int horseId = createHorse(token);
        setTransitStatus(horseId, "IN_TRANSIT");

        HttpResponse<String> response = send("PUT", "/horses/" + horseId, token,
                "{\"horseName\":\"Storm\",\"transitStatus\":\"STABLE_IDLE\",\"locationSource\":\"SYSTEM_DELIVERY\",\"currentLocationName\":\"Trại khác, Tỉnh Đồng Nai\"}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode horse = json.readTree(get("/horses/" + horseId, token).body()).path("data");
        assertThat(horse.path("transitStatus").asText()).isEqualTo("IN_TRANSIT");
        assertThat(horse.path("locationSource").asText()).isNotEqualTo("SYSTEM_DELIVERY");
        assertThat(horse.path("currentLocationName").asText()).isNotEqualTo("Trại khác, Tỉnh Đồng Nai");
    }

    private HttpResponse<String> relocate(String token, int horseId, String location, String notes) throws Exception {
        String body = json.writeValueAsString(notes == null
                ? java.util.Map.of("locationName", location, "country", "VN")
                : java.util.Map.of("locationName", location, "country", "VN", "notes", notes));
        return send("PATCH", "/horses/" + horseId + "/location", token, body);
    }

    private void setTransitStatus(int horseId, String status) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement update = connection.prepareStatement("UPDATE horse SET transit_status = ? WHERE horseid = ?")) {
            update.setString(1, status);
            update.setInt(2, horseId);
            assertThat(update.executeUpdate()).isEqualTo(1);
        }
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

    private int createHorse(String token) throws Exception {
        String chip = "985" + String.format("%012d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1_000_000_000_000L);
        HttpResponse<String> response = send("POST", "/horses", token, """
                {"horseName":"Storm","microchipID":"%s","gender":"MARE","breed":"Arabian","age":5,"weight":450}""".formatted(chip));
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 201);
        return json.readTree(response.body()).path("data").path("id").asInt();
    }

    private void insertDocuments(int horseId, List<String> types) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement insert = connection.prepareStatement(
                     "insert into horse_document (horseid, document_type, file_url, status, upload_date) values (?, ?, ?, 'PENDING', now())")) {
            for (String type : types) {
                insert.setInt(1, horseId);
                insert.setString(2, type);
                insert.setString(3, "https://files.example.com/" + type + ".pdf");
                insert.executeUpdate();
            }
        }
    }

    private HttpResponse<String> createBooking(String token, int horseId) throws Exception {
        long departAt = Instant.now().plus(30, ChronoUnit.DAYS).toEpochMilli();
        return send("POST", "/customer/bookings", token, """
                {"type":"domestic","originName":"Kho Đồng Nai","originCountry":"VN","destName":"Hà Nội","destCountry":"VN",
                 "departAt":%d,
                 "consignor":{"name":"Nguoi Gui","phone":"0900000091","idNumber":"079200000091","address":"Dong Nai"},
                 "consignee":{"name":"Nguoi Nhan","phone":"0900000097","idNumber":"079200000097","address":"Ha Noi"},
                 "horses":[{"horseId":"H-%d","name":"Storm","breed":"Arabian","sex":"mare","stall":"standard",
                            "feedPackage":"basic","waterPlan":"every_3h","insuranceOpted":false,"insurance":{"option":"none"}}]}""".formatted(departAt, horseId));
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Authorization", "Bearer " + token).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
