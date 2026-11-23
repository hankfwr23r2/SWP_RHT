package com.example.racehorse_transport.security;

import com.example.racehorse_transport.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicTrackingGuardTest {

    private static final String CODE = "BK-7F3K9QXM";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-18T08:00:00Z"));
    private final PublicTrackingGuard guard = new PublicTrackingGuard(clock);

    @Test
    void allowsLookupsBelowTheLimit() {
        for (int i = 0; i < PublicTrackingGuard.MAX_FAILURES - 1; i++) guard.recordFailure(1L);

        assertThatCode(() -> guard.checkAllowed(1L, CODE)).doesNotThrowAnyException();
    }

    @Test
    void locksTheBookingAfterTooManyWrongDigits() {
        for (int i = 0; i < PublicTrackingGuard.MAX_FAILURES; i++) guard.recordFailure(1L);

        assertThatThrownBy(() -> guard.checkAllowed(1L, CODE))
                .isInstanceOf(TooManyAttemptsException.class)
                .hasMessageContaining(CODE);
    }

    @Test
    void theLockOnlyAffectsThatBooking() {
        for (int i = 0; i < PublicTrackingGuard.MAX_FAILURES; i++) guard.recordFailure(1L);

        assertThatCode(() -> guard.checkAllowed(2L, "BK-8G4M2RWT")).doesNotThrowAnyException();
    }

    @Test
    void theLockEndsWhenTheWindowHasPassed() {
        for (int i = 0; i < PublicTrackingGuard.MAX_FAILURES; i++) guard.recordFailure(1L);

        clock.advance(PublicTrackingGuard.WINDOW);

        assertThatCode(() -> guard.checkAllowed(1L, CODE)).doesNotThrowAnyException();
    }

    @Test
    void failuresOlderThanTheWindowStartANewCount() {
        for (int i = 0; i < PublicTrackingGuard.MAX_FAILURES - 1; i++) guard.recordFailure(1L);
        clock.advance(PublicTrackingGuard.WINDOW.plusSeconds(1));

        guard.recordFailure(1L);

        assertThatCode(() -> guard.checkAllowed(1L, CODE)).doesNotThrowAnyException();
    }

    @Test
    void aCorrectLookupClearsEarlierFailures() {
        for (int i = 0; i < PublicTrackingGuard.MAX_FAILURES - 1; i++) guard.recordFailure(1L);
        guard.recordSuccess(1L);
        guard.recordFailure(1L);

        assertThatCode(() -> guard.checkAllowed(1L, CODE)).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
