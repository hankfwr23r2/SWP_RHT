package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Ngôn ngữ khách chọn trên giao diện (VI/EN) được lưu vào tài khoản để mail hóa đơn gửi đúng ngôn ngữ:
// lúc đăng ký và mỗi lần khách đổi VI/EN. Chỉ nhận "vi" hoặc "en".
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerLanguageTest {

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
    private CustomerRepository customers;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void registeringInEnglishSavesEnglish() throws Exception {
        String email = "en." + unique() + "@gmail.com";

        HttpResponse<String> response = send("POST", "/auth/register", Map.of("username", email, "email", email, "password", "Test@123",
                "fullName", "John Rider", "phone", "0901234567", "address", "Ha Noi", "language", "en"), null);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        assertThat(users.findByUsernameOrEmail(email, email).orElseThrow().getPreferredLanguage()).isEqualTo("en");
    }

    @Test
    void switchingVietnameseEnglishOnTheSiteIsSaved() throws Exception {
        String token = verifiedCustomerToken();
        String email = lastEmail;

        HttpResponse<String> toEnglish = send("PUT", "/customer/profile/language", Map.of("language", "en"), token);
        assertThat(toEnglish.statusCode()).as(toEnglish.body()).isEqualTo(200);
        assertThat(users.findByUsernameOrEmail(email, email).orElseThrow().getPreferredLanguage()).isEqualTo("en");

        assertThat(send("PUT", "/customer/profile/language", Map.of("language", "vi"), token).statusCode()).isEqualTo(200);
        assertThat(users.findByUsernameOrEmail(email, email).orElseThrow().getPreferredLanguage()).isEqualTo("vi");
    }

    @Test
    void onlyVietnameseOrEnglishIsAccepted() throws Exception {
        String token = verifiedCustomerToken();

        HttpResponse<String> response = send("PUT", "/customer/profile/language", Map.of("language", "fr"), token);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(users.findByUsernameOrEmail(lastEmail, lastEmail).orElseThrow().getPreferredLanguage()).isNull();
    }

    private String lastEmail;

    private String verifiedCustomerToken() throws Exception {
        lastEmail = "khach." + unique() + "@gmail.com";
        User user = new User();
        user.setUsername(lastEmail);
        user.setEmail(lastEmail);
        user.setPasswordHash(passwordEncoder.encode("Test@123"));
        user.setEmailVerified(true);
        user = users.save(user);
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setFullName("Khach Test");
        customers.save(customer);
        HttpResponse<String> login = send("POST", "/auth/customer/login", Map.of("username", lastEmail, "password", "Test@123"), null);
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        return json.readTree(login.body()).path("data").path("accessToken").asText();
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private HttpResponse<String> send(String method, String path, Object body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
