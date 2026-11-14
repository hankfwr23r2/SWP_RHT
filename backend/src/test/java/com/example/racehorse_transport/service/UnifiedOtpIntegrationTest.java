package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.ResetPasswordWithOtpRequest;
import com.example.racehorse_transport.entity.EmailOtp;
import com.example.racehorse_transport.entity.OtpPurpose;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.EmailOtpRepository;
import com.example.racehorse_transport.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
class UnifiedOtpIntegrationTest {
    @Autowired EmailVerificationService verification;
    @Autowired PasswordResetService reset;
    @Autowired EmailOtpRepository tokens;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ExpiredOtpCleanup cleanup;
    @MockitoBean OtpMailSender mail;

    @Test
    void concurrentIssuanceAndCooldownDoNotReplaceTheFirstToken() throws Exception {
        String email = account();
        when(mail.isConfigured()).thenReturn(true);
        Callable<Void> issue = () -> { verification.sendOtp(email); return null; };
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (var result : pool.invokeAll(List.of(issue, issue))) result.get();
        }
        EmailOtp first = tokens.findByEmailAndPurpose(email, OtpPurpose.VERIFY_EMAIL).orElseThrow();
        verification.sendOtp(email);
        EmailOtp after = tokens.findByEmailAndPurpose(email, OtpPurpose.VERIFY_EMAIL).orElseThrow();
        assertThat(after.getId()).isEqualTo(first.getId());
        assertThat(after.getOtpHash()).isEqualTo(first.getOtpHash());
        assertThat(after.getLastSentAt()).isEqualTo(first.getLastSentAt());
        verify(mail, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void purposesAreIsolatedAndFailedAttemptsCommit() {
        String email = account();
        token(email, OtpPurpose.VERIFY_EMAIL, "123456");
        token(email, OtpPurpose.RESET_PASSWORD, "654321");
        assertThatThrownBy(() -> verification.confirm(email.toUpperCase(), "654321"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(tokens.findByEmailAndPurpose(email, OtpPurpose.VERIFY_EMAIL).orElseThrow().getAttempts()).isEqualTo(1);
        assertThat(tokens.findByEmailAndPurpose(email, OtpPurpose.RESET_PASSWORD).orElseThrow().getAttempts()).isZero();
        verification.confirm(" " + email.toUpperCase() + " ", "123456");
        assertThat(tokens.findByEmailAndPurpose(email, OtpPurpose.VERIFY_EMAIL)).isEmpty();
        assertThat(tokens.findByEmailAndPurpose(email, OtpPurpose.RESET_PASSWORD)).isPresent();
        ResetPasswordWithOtpRequest request = new ResetPasswordWithOtpRequest();
        request.setEmail(email); request.setOtp("654321");
        request.setNewPassword("NewPassword123!"); request.setConfirmPassword("NewPassword123!");
        reset.resetPassword(request);
        assertThat(tokens.findByEmailAndPurpose(email, OtpPurpose.RESET_PASSWORD)).isEmpty();
        assertThat(encoder.matches("NewPassword123!", users.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void concurrentConsumptionSucceedsOnlyOnce() throws Exception {
        String email = account();
        token(email, OtpPurpose.VERIFY_EMAIL, "123456");
        Callable<Boolean> consume = () -> {
            try { verification.confirm(email, "123456"); return true; }
            catch (IllegalArgumentException expected) { return false; }
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.of(consume, consume));
            assertThat(results.get(0).get() ^ results.get(1).get()).isTrue();
        }
    }

    @Test
    void verificationAndPasswordResetDoNotOverwriteEachOthersUserUpdate() throws Exception {
        String email = account();
        token(email, OtpPurpose.VERIFY_EMAIL, "123456");
        token(email, OtpPurpose.RESET_PASSWORD, "654321");
        ResetPasswordWithOtpRequest request = new ResetPasswordWithOtpRequest();
        request.setEmail(email); request.setOtp("654321");
        request.setNewPassword("NewPassword123!"); request.setConfirmPassword("NewPassword123!");
        Callable<Void> verifyEmail = () -> { verification.confirm(email,"123456"); return null; };
        Callable<Void> resetPassword = () -> { reset.resetPassword(request); return null; };
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (var result : pool.invokeAll(List.of(verifyEmail, resetPassword))) result.get();
        }
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(encoder.matches("NewPassword123!", user.getPasswordHash())).isTrue();
        assertThat(tokens.findByEmailAndPurpose(email, OtpPurpose.VERIFY_EMAIL)).isEmpty();
        assertThat(tokens.findByEmailAndPurpose(email, OtpPurpose.RESET_PASSWORD)).isEmpty();
    }

    @Test
    void expiryAndAttemptLimitRemoveOnlyTheMatchingPurpose() {
        String email = account();
        EmailOtp expired = token(email, OtpPurpose.VERIFY_EMAIL, "123456");
        expired.setExpiresAt(Instant.now().minusSeconds(1)); tokens.saveAndFlush(expired);
        token(email, OtpPurpose.RESET_PASSWORD, "654321");
        assertThatThrownBy(() -> verification.confirm(email, "123456")).isInstanceOf(IllegalArgumentException.class);
        assertThat(tokens.findByEmailAndPurpose(email, OtpPurpose.VERIFY_EMAIL)).isEmpty();
        EmailOtp lastAttempt = token(email, OtpPurpose.VERIFY_EMAIL, "123456");
        lastAttempt.setAttempts(4); tokens.saveAndFlush(lastAttempt);
        assertThatThrownBy(() -> verification.confirm(email, "000000")).isInstanceOf(IllegalArgumentException.class);
        assertThat(tokens.findByEmailAndPurpose(email, OtpPurpose.VERIFY_EMAIL)).isEmpty();
        assertThat(tokens.findByEmailAndPurpose(email, OtpPurpose.RESET_PASSWORD)).isPresent();
    }

    @Test
    void cleanupPreservesActiveTokensAndRecentlyExpiredCooldownState() {
        String email = account();
        EmailOtp stale = token(email, OtpPurpose.VERIFY_EMAIL, "123456");
        stale.setExpiresAt(Instant.now().minusSeconds(120)); tokens.saveAndFlush(stale);
        token(email, OtpPurpose.RESET_PASSWORD, "654321");
        String recentEmail = account();
        EmailOtp recent = token(recentEmail, OtpPurpose.VERIFY_EMAIL, "123456");
        recent.setExpiresAt(Instant.now().minusSeconds(10)); tokens.saveAndFlush(recent);
        cleanup.cleanExpired();
        assertThat(tokens.findByEmailAndPurpose(email, OtpPurpose.VERIFY_EMAIL)).isEmpty();
        assertThat(tokens.findByEmailAndPurpose(email, OtpPurpose.RESET_PASSWORD)).isPresent();
        assertThat(tokens.findByEmailAndPurpose(recentEmail, OtpPurpose.VERIFY_EMAIL)).isPresent();
    }

    private String account() {
        String email = "otp-" + UUID.randomUUID() + "@example.com";
        User user = new User(); user.setUsername(email); user.setEmail(email);
        user.setPasswordHash(encoder.encode("OldPassword123!")); users.saveAndFlush(user);
        return email;
    }

    private EmailOtp token(String email, OtpPurpose purpose, String code) {
        EmailOtp token = new EmailOtp(); token.setEmail(email); token.setPurpose(purpose);
        token.setOtpHash(encoder.encode(code)); token.setExpiresAt(Instant.now().plusSeconds(600));
        token.setLastSentAt(Instant.now()); token.setAttempts(0);
        return tokens.saveAndFlush(token);
    }
}
