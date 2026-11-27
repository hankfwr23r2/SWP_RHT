package com.example.racehorse_transport.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.config.JwtProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import jakarta.servlet.http.Cookie;

class JwtUtilsTest {

    private JwtUtils jwt;

    @BeforeEach
    void setUp() {
        jwt = new JwtUtils(new JwtProperties("s".repeat(48), "jdbc:postgresql://db.example.com/db", new MockEnvironment()));
        ReflectionTestUtils.setField(jwt, "accessTokenExpirationMs", 900_000L);
        ReflectionTestUtils.setField(jwt, "refreshTokenExpirationMs", 1_296_000_000L);
        ReflectionTestUtils.setField(jwt, "cookieSameSite", "Lax");
    }

    @Test
    void accessAndRefreshTokensAreNotInterchangeable() {
        String access = jwt.generateToken("an", List.of("CUSTOMER"));
        String refresh = jwt.generateRefreshToken("an");

        assertThat(jwt.validateAccessToken(access)).isTrue();
        assertThat(jwt.validateRefreshToken(access)).isFalse();
        assertThat(jwt.validateRefreshToken(refresh)).isTrue();
        assertThat(jwt.validateAccessToken(refresh)).isFalse();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtUtils other = new JwtUtils(new JwtProperties("x".repeat(48), "jdbc:postgresql://db.example.com/db", new MockEnvironment()));
        ReflectionTestUtils.setField(other, "accessTokenExpirationMs", 900_000L);
        assertThat(jwt.validateAccessToken(other.generateToken("an", List.of("ADMIN")))).isFalse();
    }

    @Test
    void revokedTokenIsRejected() {
        String refresh = jwt.generateRefreshToken("an");
        jwt.blacklistToken(refresh);
        assertThat(jwt.validateRefreshToken(refresh)).isFalse();
    }

    @Test
    void readsOnlyTheCookieOfTheRequestedApp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("rt_customer", "khach"), new Cookie("rt_staff", "noibo"));

        assertThat(jwt.getRefreshTokenFromCookies(request, ClientApp.CUSTOMER)).isEqualTo("khach");
        assertThat(jwt.getRefreshTokenFromCookies(request, ClientApp.BACKOFFICE)).isEqualTo("noibo");
        assertThat(jwt.generateRefreshCookie("an", ClientApp.BACKOFFICE).getName()).isEqualTo("rt_staff");
        assertThat(jwt.generateRefreshCookie("an", ClientApp.BACKOFFICE).isHttpOnly()).isTrue();
    }

    @Test
    void clientAppComesFromTheHeader() {
        assertThat(ClientApp.fromHeader("backoffice")).contains(ClientApp.BACKOFFICE);
        assertThat(ClientApp.fromHeader(" Customer ")).contains(ClientApp.CUSTOMER);
        assertThat(ClientApp.fromHeader("admin")).isEmpty();
        assertThat(ClientApp.fromHeader(null)).isEmpty();
    }
}
