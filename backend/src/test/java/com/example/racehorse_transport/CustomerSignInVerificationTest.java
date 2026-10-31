package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
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
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

// Đăng ký chỉ nhận @gmail.com và phải nhập OTP gửi về email; tài khoản khách cũ chưa xác thực cũng phải nhập OTP khi đăng nhập.
// Tài khoản nội bộ đăng nhập như cũ. Email đi qua SMTP thật chạy trong test (GreenMail).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerSignInVerificationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final GreenMail SMTP = new GreenMail(ServerSetupTest.SMTP.dynamicPort());

    static {
        POSTGRES.start();
        SMTP.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> SMTP.getSmtp().getPort());
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
    }

    @AfterAll
    static void stopServers() {
        SMTP.stop();
    }

    @LocalServerPort
    private int port;

    @Autowired private UserRepository users;
    @Autowired private CustomerRepository customers;
    @Autowired private StaffRepository staff;
    @Autowired private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void registerRejectsEmailThatIsNotGmail() throws Exception {
        HttpResponse<String> response = post("/auth/register", registerBody("an." + unique() + "@yahoo.com"), null);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(users.findByEmailIgnoreCase(lastEmail).isPresent()).isFalse();
    }

    @Test
    void gmailRegistrationSignsInOnlyAfterTheEmailedOtp() throws Exception {
        String email = "khach." + unique() + "@gmail.com";
        assertThat(post("/auth/register", registerBody(email), null).statusCode()).isEqualTo(201);
        String otp = otpSentTo(email);

        // Chưa nhập OTP thì đăng nhập không nhận token, chỉ được yêu cầu xác thực email
        JsonNode beforeOtp = data(post("/auth/customer/login", Map.of("username", email, "password", "Test@123"), null));
        assertThat(beforeOtp.path("emailVerificationRequired").asBoolean()).isTrue();
        assertThat(beforeOtp.path("accessToken").isMissingNode() || beforeOtp.path("accessToken").isNull()).isTrue();

        HttpResponse<String> wrong = post("/auth/email-verification/confirm", Map.of("email", email, "otp", wrongOtp(otp)), null);
        assertThat(wrong.statusCode()).isEqualTo(400);

        JsonNode confirmed = data(post("/auth/email-verification/confirm", Map.of("email", email, "otp", otp), null));
        assertThat(confirmed.path("accessToken").asText()).isNotBlank();
        assertThat(confirmed.path("role").asText()).isEqualTo("CUSTOMER");

        JsonNode login = data(post("/auth/customer/login", Map.of("username", email, "password", "Test@123"), null));
        assertThat(login.path("accessToken").asText()).isNotBlank();
        assertThat(login.path("emailVerificationRequired").asBoolean()).isFalse();
    }

    @Test
    void existingUnverifiedCustomerGetsAnOtpWhenSigningIn() throws Exception {
        String email = "cu." + unique() + "@example.com";
        saveCustomer(email, null, "0901234567");

        JsonNode login = data(post("/auth/customer/login", Map.of("username", email, "password", "Test@123"), null));
        assertThat(login.path("emailVerificationRequired").asBoolean()).isTrue();

        JsonNode confirmed = data(post("/auth/email-verification/confirm", Map.of("email", email, "otp", otpSentTo(email)), null));
        assertThat(confirmed.path("accessToken").asText()).isNotBlank();
    }

    @Test
    void staffWithFakeEmailSignsInWithoutOtp() throws Exception {
        String email = "nv." + unique() + "@noibo.local";
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Test@123"));
        user = users.save(user);
        Staff member = new Staff();
        member.setUser(user);
        member.setRole("MANAGER");
        member.setStaffCode("MGR-" + unique());
        member.setHireDate(LocalDate.now());
        member.setEmploymentStatus("ACTIVE");
        staff.save(member);

        JsonNode login = data(post("/auth/staff/login", Map.of("username", email, "password", "Test@123"), null));
        assertThat(login.path("accessToken").asText()).isNotBlank();
    }

    // ===== Hỗ trợ =====

    private String lastEmail;

    private Map<String, String> registerBody(String email) {
        lastEmail = email;
        return Map.of("username", email, "email", email, "password", "Test@123",
                "fullName", "Nguyen Van Khach", "phone", "0901234567", "address", "Ha Noi");
    }

    private Customer saveCustomer(String email, Boolean emailVerified, String phone) {
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Test@123"));
        user.setEmailVerified(emailVerified);
        user = users.save(user);
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setFullName("Khach Cu");
        customer.setPhone(phone);
        return customers.save(customer);
    }

    private String otpSentTo(String email) throws Exception {
        Pattern sixDigits = Pattern.compile("\\b(\\d{6})\\b");
        for (int i = 0; i < 50; i++) {
            MimeMessage[] mails = SMTP.getReceivedMessagesForDomain(email);
            for (int j = mails.length - 1; j >= 0; j--) {
                Matcher m = sixDigits.matcher(GreenMailUtil.getBody(mails[j]));
                if (m.find()) return m.group(1);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Không thấy email OTP gửi tới " + email + "; đã nhận: "
                + Arrays.stream(SMTP.getReceivedMessages()).map(GreenMailUtil::getBody).toList());
    }

    private static String wrongOtp(String otp) {
        return otp.equals("000000") ? "111111" : "000000";
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private JsonNode data(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
        return json.readTree(response.body()).path("data");
    }

    private HttpResponse<String> post(String path, Object body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
