package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Random;
import org.junit.jupiter.api.Test;

class OtpPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-23T08:00:00Z");

    @Test
    void normalizesEmail() {
        assertThat(OtpPolicy.normalizeEmail("  Khach.Hang@Gmail.COM ")).isEqualTo("khach.hang@gmail.com");
        assertThat(OtpPolicy.normalizeEmail(null)).isEmpty();
    }

    @Test
    void codeHasSixDigitsWithLeadingZeros() {
        Random alwaysSmall = new Random() {
            @Override
            public int nextInt(int bound) {
                return 42;
            }
        };
        assertThat(OtpPolicy.generateCode(alwaysSmall)).isEqualTo("000042");
        assertThat(OtpPolicy.generateCode(new Random(7))).matches("\\d{6}");
    }

    @Test
    void resendWaitsForCooldown() {
        assertThat(OtpPolicy.canSendAgain(null, NOW)).isTrue();
        assertThat(OtpPolicy.canSendAgain(NOW.minusSeconds(30), NOW)).isFalse();
        assertThat(OtpPolicy.canSendAgain(NOW.minus(OtpPolicy.RESEND_COOLDOWN), NOW)).isTrue();
    }

    @Test
    void codeIsUsableOnlyBeforeExpiryAndBelowMaxAttempts() {
        Instant expiresAt = OtpPolicy.expiryOf(NOW, 10);
        assertThat(expiresAt).isEqualTo(NOW.plusSeconds(600));
        assertThat(OtpPolicy.isUsable(expiresAt, 0, 5, NOW)).isTrue();
        assertThat(OtpPolicy.isUsable(expiresAt, 5, 5, NOW)).isFalse();
        assertThat(OtpPolicy.isUsable(expiresAt, 0, 5, expiresAt)).isFalse();
    }

    @Test
    void codeIsExhaustedAtMaxAttempts() {
        assertThat(OtpPolicy.isExhausted(4, 5)).isFalse();
        assertThat(OtpPolicy.isExhausted(5, 5)).isTrue();
    }
}
