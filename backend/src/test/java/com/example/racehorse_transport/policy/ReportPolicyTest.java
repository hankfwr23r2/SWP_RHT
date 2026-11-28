package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReportPolicyTest {

    private static final Instant PLANNED = Instant.parse("2031-03-05T02:00:00Z");

    @Test
    void lateOnlyWhenArrivingMoreThanThirtyMinutesAfterPlan() {
        assertThat(ReportPolicy.isLate(PLANNED, PLANNED.plusSeconds(30 * 60))).isFalse();
        assertThat(ReportPolicy.isLate(PLANNED, PLANNED.plusSeconds(30 * 60 + 1))).isTrue();
        assertThat(ReportPolicy.isLate(PLANNED, PLANNED.minusSeconds(600))).isFalse();
        assertThat(ReportPolicy.isLate(PLANNED, null)).isFalse();
        assertThat(ReportPolicy.isLate(null, PLANNED)).isFalse();
    }

    @Test
    void onTimeRateNeedsAtLeastOneArrival() {
        assertThat(ReportPolicy.onTimeRate(0, 0)).isNull();
        assertThat(ReportPolicy.onTimeRate(4, 1)).isEqualTo(0.75);
    }
}
