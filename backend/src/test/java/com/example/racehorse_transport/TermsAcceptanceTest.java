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
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Bằng chứng khách đồng ý điều khoản, lưu ở bảng terms_acceptance (document_code, document_version, accepted_at,
// user_id, booking_id, horse_id):
// - Đăng ký tài khoản phải đồng ý Điều khoản vận chuyển (acceptedTerms = true); không thì không tạo tài khoản.
// - Đặt đơn có ngựa không mua bảo hiểm phải đồng ý Điều khoản Trách nhiệm Hạn chế (limitedLiabilityAccepted = true);
//   mỗi ngựa bị từ chối bảo hiểm có một dòng ghi nhận gắn với đơn và ngựa đó.
// - Phiên bản là phiên bản đang hiệu lực ở server, giờ đồng ý do server ghi, khách không tự đặt được.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockTurnstileVerification
class TermsAcceptanceTest {

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

    private record Acceptance(String documentCode, String version, Timestamp acceptedAt, Integer bookingId, Integer horseId) {}

    @Test
    void registrationWithoutAcceptingTheTermsCreatesNoAccount() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "khach" + suffix + "@gmail.com";

        HttpResponse<String> missing = register(suffix, email, "");
        HttpResponse<String> refused = register(suffix, email, ",\"acceptedTerms\":false");

        assertThat(missing.statusCode()).as(missing.body()).isBetween(400, 499);
        assertThat(refused.statusCode()).as(refused.body()).isBetween(400, 499);
        assertThat(json.readTree(refused.body()).path("message").asText()).contains("Điều khoản");
        assertThat(count("select count(*) from users where email = ?", email)).isZero();
    }

    @Test
    void acceptingTheTermsAtRegistrationIsRecordedWithTheServerTime() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "khach" + suffix + "@gmail.com";

        // Khách gửi kèm giờ tự khai nhưng giờ lưu phải là giờ server
        HttpResponse<String> response = register(suffix, email, ",\"acceptedTerms\":true,\"acceptedAt\":\"2020-01-01T00:00:00Z\"");

        assertThat(response.statusCode()).as(response.body()).isBetween(200, 201);
        List<Acceptance> saved = acceptancesOfUser(email);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).documentCode()).isEqualTo("TRANSPORT_TERMS");
        assertThat(saved.get(0).version()).isNotBlank();
        assertThat(saved.get(0).bookingId()).isNull();
        assertThat(saved.get(0).horseId()).isNull();
        assertThat(saved.get(0).acceptedAt().toInstant()).isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(5));
    }

    @Test
    void bookingHorseWithoutInsuranceNeedsTheLimitedLiabilityTerms() throws Exception {
        String token = registerAndLogin();
        int horseId = createHorse(token);
        insertDocuments(horseId, List.of("PASSPORT", "VACCINATION", "BLOOD_TEST"));

        HttpResponse<String> response = createBooking(token, horseId, "");

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(json.readTree(response.body()).path("message").asText()).contains("Trách nhiệm Hạn chế");
        assertThat(count("select count(*) from terms_acceptance where document_code = 'LIMITED_LIABILITY' and horse_id = ?", horseId)).isZero();
    }

    @Test
    void acceptedLimitedLiabilityIsRecordedForTheBookingAndTheHorse() throws Exception {
        String token = registerAndLogin();
        int horseId = createHorse(token);
        insertDocuments(horseId, List.of("PASSPORT", "VACCINATION", "BLOOD_TEST"));

        HttpResponse<String> response = createBooking(token, horseId, ",\"limitedLiabilityAccepted\":true,\"acceptedAt\":\"2020-01-01T00:00:00Z\"");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        String code = json.readTree(response.body()).path("data").path("id").asText();
        int bookingId = Integer.parseInt(text("select bookingid::text from booking where booking_code = ?", code));
        List<Acceptance> saved = acceptancesOfBooking(bookingId);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).documentCode()).isEqualTo("LIMITED_LIABILITY");
        assertThat(saved.get(0).horseId()).isEqualTo(horseId);
        assertThat(saved.get(0).version()).isNotBlank();
        assertThat(saved.get(0).acceptedAt().toInstant()).isBetween(Instant.now().minusSeconds(60), Instant.now().plusSeconds(5));
    }

    private HttpResponse<String> register(String suffix, String email, String extra) throws Exception {
        return post("/auth/register", null, """
                {"username":"khach%s","email":"%s","password":"Test@123","fullName":"Khach Test",
                 "phone":"0900000098","address":"Ha Noi"%s}""".formatted(suffix, email, extra));
    }

    private String registerAndLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "khach" + suffix + "@gmail.com";
        HttpResponse<String> register = register(suffix, email, ",\"acceptedTerms\":true");
        assertThat(register.statusCode()).as(register.body()).isBetween(200, 201);
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
        String chip = "985" + String.format("%012d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1_000_000_000_000L);
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
                insert.setString(3, "https://files.example.com/" + type + "-" + UUID.randomUUID() + ".pdf");
                insert.executeUpdate();
            }
        }
    }

    private HttpResponse<String> createBooking(String token, int horseId, String extra) throws Exception {
        long departAt = Instant.now().plus(30, ChronoUnit.DAYS).toEpochMilli();
        return post("/customer/bookings", token, """
                {"type":"domestic","originName":"Kho Đồng Nai","originCountry":"VN","destName":"Hà Nội","destCountry":"VN",
                 "departAt":%d,
                 "consignor":{"name":"Nguoi Gui","phone":"0900000091","idNumber":"079200000091","address":"Dong Nai"},
                 "consignee":{"name":"Nguoi Nhan","phone":"0900000097","idNumber":"079200000097","address":"Ha Noi"},
                 "horses":[{"horseId":"H-%d","name":"Storm","breed":"Arabian","sex":"mare","stall":"standard",
                            "feedPackage":"basic","waterPlan":"every_3h","insuranceOpted":false,"insurance":{"option":"none"}}]%s}""".formatted(departAt, horseId, extra));
    }

    private List<Acceptance> acceptancesOfUser(String email) throws Exception {
        return acceptances("select a.document_code, a.document_version, a.accepted_at, a.booking_id, a.horse_id from terms_acceptance a join users u on u.userid = a.user_id where u.email = ?", email);
    }

    private List<Acceptance> acceptancesOfBooking(int bookingId) throws Exception {
        return acceptances("select document_code, document_version, accepted_at, booking_id, horse_id from terms_acceptance where booking_id = ?", bookingId);
    }

    private List<Acceptance> acceptances(String sql, Object parameter) throws Exception {
        List<Acceptance> found = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement query = connection.prepareStatement(sql)) {
            query.setObject(1, parameter);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    found.add(new Acceptance(rows.getString(1), rows.getString(2), rows.getTimestamp(3),
                            (Integer) rows.getObject(4), (Integer) rows.getObject(5)));
                }
            }
        }
        return found;
    }

    private int count(String sql, Object parameter) throws Exception {
        return Integer.parseInt(text(sql.replace("count(*)", "count(*)::text"), parameter));
    }

    private String text(String sql, Object parameter) throws Exception {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             PreparedStatement query = connection.prepareStatement(sql)) {
            query.setObject(1, parameter);
            try (ResultSet rows = query.executeQuery()) {
                assertThat(rows.next()).as(sql).isTrue();
                return rows.getString(1);
            }
        }
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
