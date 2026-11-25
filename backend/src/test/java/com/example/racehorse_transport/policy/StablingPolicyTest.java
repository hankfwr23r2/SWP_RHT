package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StablingPolicyTest {

    private static final Instant START = Instant.parse("2031-01-15T03:00:00Z");

    @Test
    void partialDaysRoundUpAndAtLeastOneDayIsCharged() {
        assertThat(StablingPolicy.days(START, START)).isEqualTo(1);
        assertThat(StablingPolicy.days(START, START.plus(Duration.ofHours(23)))).isEqualTo(1);
        assertThat(StablingPolicy.days(START, START.plus(Duration.ofHours(24)))).isEqualTo(1);
        assertThat(StablingPolicy.days(START, START.plus(Duration.ofDays(1)).plusNanos(1))).isEqualTo(2);
        assertThat(StablingPolicy.days(START, START.plus(Duration.ofHours(25)))).isEqualTo(2);
        assertThat(StablingPolicy.fee(START, START.plus(Duration.ofDays(3)), 1_000_000)).isEqualByComparingTo(BigDecimal.valueOf(3_000_000));
    }
}
