package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.MockTurnstileVerification;
import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.client.MailClient;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.PartyRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.TermsAcceptanceRepository;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// Luồng auth trên bảng mới: đăng ký -> xác thực email -> đăng nhập -> làm mới token -> đổi mật khẩu,
// quên mật khẩu, vai trò lấy từ app_user, và token của tài khoản bị khóa bị từ chối.
@PostgresIntegrationTest
@MockTurnstileVerification
@AutoConfigureMockMvc
class AuthFlowTest {

    private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");

    @Autowired private MockMvc mvc;
    @Autowired private TestData testData;
    @Autowired private AppUserRepository users;
    @Autowired private CustomerRepository customers;
    @Autowired private StaffRepository staff;
    @Autowired private PartyRepository parties;
    @Autowired private TermsAcceptanceRepository termsAcceptances;

    @MockitoBean private MailClient mail;

    @BeforeEach
    void mailIsConfigured() {
        when(mail.isConfigured()).thenReturn(true);
    }

    @Test
    void registerVerifyLoginRefreshAndChangePassword() throws Exception {
        String username = testData.unique("khach").replace("-", "");
        String email = username + "@gmail.com";
        long usersBefore = users.count();
        long customersBefore = customers.count();
        long partiesBefore = parties.count();

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s","password":"Matkhau@1","fullName":"Nguyễn Văn An",
                         "phone":"0909123456","acceptedTerms":true,"language":"en"}""".formatted(username, email)))
                .andExpect(status().isCreated());

        AppUser user = users.findByUsername(username).orElseThrow();
        assertThat(user.getEmailVerified()).isFalse();
        assertThat(user.getPreferredLanguage()).isEqualTo("en");
        assertThat(user.getRole().getCode()).isEqualTo("CUSTOMER");
        Customer customer = customers.findById(user.getId()).orElseThrow();
        assertThat(customer.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(customer.getPhone()).isEqualTo("0909123456");
        // Đúng một tài khoản và một hồ sơ khách, không có hồ sơ nhân viên, không tạo party
        assertThat(users.count()).isEqualTo(usersBefore + 1);
        assertThat(customers.count()).isEqualTo(customersBefore + 1);
        assertThat(parties.count()).isEqualTo(partiesBefore);
        assertThat(staff.findById(user.getId())).isEmpty();
        assertThat(termsAcceptances.findAll()).anySatisfy(t -> assertThat(t.getUser().getId()).isEqualTo(user.getId()));

        // Chưa xác thực email: đăng nhập không cấp token
        mvc.perform(post("/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Matkhau@1\"}".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.emailVerificationRequired").value(true))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());

        MvcResult confirmed = mvc.perform(post("/auth/email-verification/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"otp\":\"%s\"}".formatted(email, lastCodeSentTo(email))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Văn An"))
                .andReturn();
        String accessToken = JsonPath.read(confirmed.getResponse().getContentAsString(), "$.data.accessToken");
        Cookie refreshCookie = confirmed.getResponse().getCookie("rt_customer");
        assertThat(refreshCookie).isNotNull();

        mvc.perform(post("/auth/refresh-token").header("X-Client-App", "customer").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.roles[0]").value("CUSTOMER"));

        mvc.perform(post("/auth/change-password").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Matkhau@1\",\"newPassword\":\"Matkhau@2\",\"confirmPassword\":\"Matkhau@2\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Matkhau@2\"}".formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void wrongCodeFiveTimesDestroysTheCode() throws Exception {
        String username = testData.unique("sai").replace("-", "");
        String email = username + "@gmail.com";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s","password":"Matkhau@1","fullName":"Lê Thị Bình",
                         "phone":"0909123457","acceptedTerms":true}""".formatted(username, email)))
                .andExpect(status().isCreated());
        String code = lastCodeSentTo(email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/auth/email-verification/confirm").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"%s\",\"otp\":\"%s\"}".formatted(email, wrong)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/auth/email-verification/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"otp\":\"%s\"}".formatted(email, code)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrationRequiresAcceptedTerms() throws Exception {
        String username = testData.unique("khongdongy").replace("-", "");
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","email":"%s@gmail.com","password":"Matkhau@1","fullName":"Phạm Văn Cường",
                         "phone":"0909123458","acceptedTerms":false}""".formatted(username, username)))
                .andExpect(status().isBadRequest());
        assertThat(users.findByUsername(username)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MANAGER", "COORDINATOR", "SPECIALIST", "DRIVER", "ESCORT"})
    void staffLoginUsesRoleOfAccount(String roleCode) throws Exception {
        String username = testData.unique("nhanvien");
        testData.staff(username, roleCode);

        mvc.perform(post("/auth/staff/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, TestData.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value(roleCode))
                .andExpect(jsonPath("$.data.roles.length()").value(1))
                .andExpect(jsonPath("$.data.roles[0]").value(roleCode))
                .andExpect(jsonPath("$.data.staffCode").isNotEmpty());

        // Tài khoản nhân viên không đăng nhập được ở cổng khách
        mvc.perform(post("/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, TestData.PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerCannotLogInToBackoffice() throws Exception {
        String username = testData.unique("khachnoibo");
        testData.customer(username);

        mvc.perform(post("/auth/staff/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, TestData.PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenOfLockedAccountIsRejected() throws Exception {
        String username = testData.unique("bikhoa");
        testData.customer(username);
        MvcResult login = mvc.perform(post("/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, TestData.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
        String wrongCurrentPassword = "{\"currentPassword\":\"Sai@1234\",\"newPassword\":\"Matkhau@9\",\"confirmPassword\":\"Matkhau@9\"}";

        // Còn hoạt động: được xác thực, chỉ sai mật khẩu hiện tại
        mvc.perform(post("/auth/change-password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(wrongCurrentPassword))
                .andExpect(status().isBadRequest());

        AppUser user = users.findByUsername(username).orElseThrow();
        user.setStatus(AppUserStatus.LOCKED);
        users.saveAndFlush(user);

        // Bị khóa: token cũ không còn xác thực được
        mvc.perform(post("/auth/change-password").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(wrongCurrentPassword))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eachAppKeepsItsOwnRefreshCookie() throws Exception {
        String customerName = testData.unique("khachcookie");
        testData.customer(customerName);
        String staffName = testData.unique("nvcookie");
        testData.staff(staffName, "MANAGER");

        Cookie customerCookie = login("/auth/customer/login", customerName).getResponse().getCookie("rt_customer");
        MvcResult staffLogin = login("/auth/staff/login", staffName);
        Cookie staffCookie = staffLogin.getResponse().getCookie("rt_staff");
        assertThat(customerCookie).isNotNull();
        assertThat(staffCookie).isNotNull();
        assertThat(staffLogin.getResponse().getCookie("rt_customer")).isNull();

        // Cùng trình duyệt giữ cả hai cookie: mỗi app nhận đúng tài khoản của mình
        mvc.perform(post("/auth/refresh-token").header("X-Client-App", "backoffice").cookie(customerCookie, staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(staffName));
        mvc.perform(post("/auth/refresh-token").header("X-Client-App", "customer").cookie(customerCookie, staffCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(customerName));

        // Cookie đặt nhầm tên không đổi được phiên của app kia
        Cookie staffTokenAsCustomer = new Cookie("rt_customer", staffCookie.getValue());
        mvc.perform(post("/auth/refresh-token").header("X-Client-App", "customer").cookie(staffTokenAsCustomer))
                .andExpect(status().isUnauthorized());
        // Thiếu header ứng dụng hoặc thiếu cookie: 401 để frontend đưa về trang đăng nhập
        mvc.perform(post("/auth/refresh-token").cookie(customerCookie))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh-token").header("X-Client-App", "customer"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccessTokenAndViceVersa() throws Exception {
        String username = testData.unique("khachloai");
        testData.customer(username);
        MvcResult login = login("/auth/customer/login", username);
        String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
        String refreshToken = login.getResponse().getCookie("rt_customer").getValue();
        String wrongCurrentPassword = "{\"currentPassword\":\"Sai@1234\",\"newPassword\":\"Matkhau@9\",\"confirmPassword\":\"Matkhau@9\"}";

        mvc.perform(post("/auth/change-password").header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken)
                        .contentType(MediaType.APPLICATION_JSON).content(wrongCurrentPassword))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh-token").header("X-Client-App", "customer").cookie(new Cookie("rt_customer", accessToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesOnlyTheCurrentAppSession() throws Exception {
        String customerName = testData.unique("khachra");
        testData.customer(customerName);
        String staffName = testData.unique("nvra");
        testData.staff(staffName, "COORDINATOR");
        Cookie customerCookie = login("/auth/customer/login", customerName).getResponse().getCookie("rt_customer");
        Cookie staffCookie = login("/auth/staff/login", staffName).getResponse().getCookie("rt_staff");

        MvcResult logout = mvc.perform(post("/auth/logout").header("X-Client-App", "backoffice").cookie(customerCookie, staffCookie))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(logout.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .hasSize(1)
                .allSatisfy(header -> assertThat(header).startsWith("rt_staff=").contains("Max-Age=0"));

        mvc.perform(post("/auth/refresh-token").header("X-Client-App", "backoffice").cookie(staffCookie))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh-token").header("X-Client-App", "customer").cookie(customerCookie))
                .andExpect(status().isOk());
    }

    private MvcResult login(String path, String username) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, TestData.PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void passwordResetWithEmailedCode() throws Exception {
        String username = testData.unique("quenmk");
        String email = testData.customer(username).getUser().getEmail();

        mvc.perform(post("/auth/password-reset/request-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isOk());

        mvc.perform(post("/auth/password-reset/complete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"otp\":\"%s\",\"newPassword\":\"Moi@12345\",\"confirmPassword\":\"Moi@12345\"}"
                                .formatted(email, lastCodeSentTo(email))))
                .andExpect(status().isOk());

        mvc.perform(post("/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"Moi@12345\"}".formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    // Mã OTP trong email gần nhất gửi tới địa chỉ này
    private String lastCodeSentTo(String email) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).send(eq(email), anyString(), body.capture());
        List<String> bodies = body.getAllValues();
        Matcher matcher = SIX_DIGITS.matcher(bodies.get(bodies.size() - 1));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
