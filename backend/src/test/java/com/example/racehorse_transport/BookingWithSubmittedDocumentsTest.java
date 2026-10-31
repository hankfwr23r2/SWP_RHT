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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Khách tải giấy tờ ngựa lên là lên đơn được ngay; không có bước "chờ duyệt" trước khi đặt.
// Chuyên viên thẩm định hồ sơ ngựa sau, trong từng đơn. Ngựa còn thiếu giấy tờ bắt buộc thì vẫn không đặt được.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingWithSubmittedDocumentsTest {

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
    void customerBooksHorseRightAfterUploadingDocuments() throws Exception {
        String token = registerAndLogin();
        int horseId = createHorse(token);
        // Như khách vừa tải lên: giấy tờ ở trạng thái mặc định khi upload, chưa ai duyệt
        insertDocuments(horseId, List.of("PASSPORT", "VACCINATION", "BLOOD_TEST"));

        HttpResponse<String> response = createBooking(token, horseId);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        assertThat(json.readTree(response.body()).path("data").path("status").asText()).isEqualTo("pending_intake");
    }

    @Test
    void horseMissingARequiredDocumentStillCannotBeBooked() throws Exception {
        String token = registerAndLogin();
        int horseId = createHorse(token);
        insertDocuments(horseId, List.of("PASSPORT", "VACCINATION"));

        HttpResponse<String> response = createBooking(token, horseId);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(json.readTree(response.body()).path("message").asText()).contains("Kết quả xét nghiệm máu");
    }

    private String registerAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "khach" + suffix + "@gmail.com";
        HttpResponse<String> register = post("/auth/register", null, """
                {"username":"khach%s","email":"%s","password":"Test@123","fullName":"Khach Test",
                 "phone":"0900000098","address":"Ha Noi"}""".formatted(suffix, email));
        assertThat(register.statusCode()).as(register.body()).isBetween(200, 201);
        // Test này không kiểm luồng OTP email (đã có CustomerSignInVerificationTest): đánh dấu email đã xác thực
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement verify = connection.prepareStatement("UPDATE users SET email_verified = true WHERE email = ?")) {
            verify.setString(1, email);
            assertThat(verify.executeUpdate()).isEqualTo(1);
        }
        HttpResponse<String> login = post("/auth/customer/login", null,
                "{\"username\":\"%s\",\"password\":\"Test@123\"}".formatted(email));
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        return json.readTree(login.body()).path("data").path("accessToken").asText();
    }

    private int createHorse(String token) throws Exception {
        String chip = "CHIP" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        HttpResponse<String> response = post("/horses", token, """
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
        return post("/customer/bookings", token, """
                {"type":"domestic","originName":"Kho Đồng Nai","originCountry":"VN","destName":"Hà Nội","destCountry":"VN",
                 "departAt":%d,
                 "consignor":{"name":"Nguoi Gui","phone":"0900000091","idNumber":"079200000091","address":"Dong Nai"},
                 "consignee":{"name":"Nguoi Nhan","phone":"0900000097","idNumber":"079200000097","address":"Ha Noi"},
                 "horses":[{"horseId":"H-%d","name":"Storm","breed":"Arabian","sex":"mare","stall":"standard",
                            "feedPackage":"basic","waterPlan":"every_3h","insuranceOpted":false}]}""".formatted(departAt, horseId));
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

}
