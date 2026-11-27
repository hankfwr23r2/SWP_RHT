package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LoginAttemptPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");

    @Test
    void countsFailuresUntilTheLimitThenLocks() {
        assertThat(LoginAttemptPolicy.afterFailure(0, 5, 15, NOW)).isEqualTo(new LoginAttemptPolicy.Attempts(1, null));
        assertThat(LoginAttemptPolicy.afterFailure(3, 5, 15, NOW)).isEqualTo(new LoginAttemptPolicy.Attempts(4, null));
        assertThat(LoginAttemptPolicy.afterFailure(4, 5, 15, NOW))
                .isEqualTo(new LoginAttemptPolicy.Attempts(0, Instant.parse("2026-11-27T10:15:00Z")));
    }

    @Test
    void lockLastsUntilTheDeadline() {
        Instant until = Instant.parse("2026-11-27T10:15:00Z");
        assertThat(LoginAttemptPolicy.isLocked(until, NOW)).isTrue();
        assertThat(LoginAttemptPolicy.isLocked(until, until)).isFalse();
        assertThat(LoginAttemptPolicy.isLocked(null, NOW)).isFalse();
        assertThat(LoginAttemptPolicy.minutesLeft(until, NOW)).isEqualTo(15);
        assertThat(LoginAttemptPolicy.minutesLeft(until, until.minusSeconds(10))).isEqualTo(1);
    }

    @Test
    void successClearsTheCounter() {
        assertThat(LoginAttemptPolicy.afterSuccess()).isEqualTo(new LoginAttemptPolicy.Attempts(0, null));
    }

    @Test
    void rejectsNonPositiveSettings() {
        assertThatThrownBy(() -> LoginAttemptPolicy.afterFailure(0, 0, 15, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LoginAttemptPolicy.afterFailure(0, 5, 0, NOW)).isInstanceOf(IllegalArgumentException.class);
    }
}
