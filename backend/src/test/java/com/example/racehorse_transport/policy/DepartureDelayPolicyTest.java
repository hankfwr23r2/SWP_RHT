package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.policy.DepartureDelayPolicy.Thresholds;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DepartureDelayPolicyTest {
    private static final Instant T = Instant.parse("2027-01-15T03:00:00Z");
    private static final Thresholds LIMITS = new Thresholds(24, 30, 2, 5);

    @Test
    void reminderAndDelayApplyAtTheirExactConfiguredBounds() {
        Instant reminder = T.minus(Duration.ofHours(24));
        assertThat(DepartureDelayPolicy.needsReminder(T, false, reminder.minusSeconds(1), LIMITS)).isFalse();
        assertThat(DepartureDelayPolicy.needsReminder(T, false, reminder, LIMITS)).isTrue();
        assertThat(DepartureDelayPolicy.needsReminder(T, true, reminder, LIMITS)).isFalse();
        assertThat(DepartureDelayPolicy.isLate(T, false, T.minusSeconds(1), LIMITS)).isFalse();
        assertThat(DepartureDelayPolicy.isLate(T, false, T, LIMITS)).isTrue();
        assertThat(DepartureDelayPolicy.isLate(T, true, T.plusSeconds(1799), LIMITS)).isFalse();
        assertThat(DepartureDelayPolicy.isLate(T, true, T.plusSeconds(1800), LIMITS)).isTrue();
    }

    @Test
    void repeatedChangesCountFromTheOriginalTimeAndAFutureSchedulePreventsAutoCancellation() {
        assertThat(DepartureDelayPolicy.permitsReschedule(delayBy(1), LIMITS)).isFalse();
        assertThat(DepartureDelayPolicy.permitsReschedule(delayBy(2), LIMITS)).isTrue();
        assertThat(DepartureDelayPolicy.permitsRefund(delayBy(3), LIMITS)).isFalse();
        Instant revised = T.plus(Duration.ofHours(3)).plus(Duration.ofHours(2));
        assertThat(DepartureDelayPolicy.permitsRefund(DepartureDelayPolicy.delay(T, revised, T), LIMITS)).isTrue();
        assertThat(DepartureDelayPolicy.cancellationDue(T, null, revised.minusSeconds(1), LIMITS)).isFalse();
        assertThat(DepartureDelayPolicy.cancellationDue(T, null, revised, LIMITS)).isTrue();
        assertThat(DepartureDelayPolicy.cancellationDue(T, revised, revised, LIMITS)).isFalse();
    }

    @Test
    void blocksRevisionOfAnAlreadyStartedDepartureAndPastTimes() {
        assertThatThrownBy(() -> DepartureDelayPolicy.requireRevisable(DepartureStatus.IN_PROGRESS, T, T.plusSeconds(1), T))
                .isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> DepartureDelayPolicy.requireRevisable(DepartureStatus.CLOSED, T, T.plusSeconds(1), T.plusSeconds(2)))
                .isInstanceOf(BookingRuleException.class);
    }

    private static Duration delayBy(int hours) {
        return DepartureDelayPolicy.delay(T, T.plus(Duration.ofHours(hours)), T);
    }
}
