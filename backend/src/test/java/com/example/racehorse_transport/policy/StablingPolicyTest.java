package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.policy.StablingPolicy.Hours;
import com.example.racehorse_transport.policy.StablingPolicy.Run;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class StablingPolicyTest {
    private static final Hours HOURS = new Hours(LocalTime.of(6, 0), LocalTime.of(18, 0), LocalTime.of(8, 0), ZoneId.of("Asia/Ho_Chi_Minh"));
    // 10:00 giờ Việt Nam
    private static final Instant START = Instant.parse("2031-01-15T03:00:00Z");

    private static long after(Duration stay) {
        return StablingPolicy.blocks(List.of(new Run(START, null)), START.plus(stay), HOURS, 30);
    }

    @Test
    void countsOnlyWholeTwentyFourHourBlocks() {
        assertThat(after(Duration.ZERO)).isZero();
        assertThat(after(Duration.ofHours(24).minusSeconds(1))).isZero();
        assertThat(after(Duration.ofHours(24))).isEqualTo(1);
        assertThat(after(Duration.ofHours(48).minusSeconds(1))).isEqualTo(1);
        assertThat(after(Duration.ofHours(68))).isEqualTo(2);
        assertThat(after(Duration.ofHours(72))).isEqualTo(3);
        assertThat(StablingPolicy.fee(2, new BigDecimal("1000000"))).isEqualByComparingTo("2000000");
    }

    @Test
    void stopsCountingAtTheCap() {
        assertThat(after(Duration.ofDays(90))).isEqualTo(30);
    }

    @Test
    void addsSeparateStaysOfTheSameHorse() {
        var first = new Run(START, START.plus(Duration.ofHours(20)));
        var secondStart = START.plus(Duration.ofDays(3));
        var runs = List.of(new Run(secondStart, null), first);
        assertThat(StablingPolicy.blocks(runs, secondStart.plus(Duration.ofHours(3)), HOURS, 30)).isZero();
        assertThat(StablingPolicy.blocks(runs, secondStart.plus(Duration.ofHours(4)), HOURS, 30)).isEqualTo(1);
        assertThat(StablingPolicy.blocks(runs, secondStart.plus(Duration.ofHours(20)), HOURS, 30)).isEqualTo(1);
    }

    @Test
    void markOutsideOpeningHoursMovesToTheNextMorning() {
        var evening = Instant.parse("2031-01-15T12:00:00Z"); // 19:00
        var runs = List.of(new Run(evening, null));
        var morning = Instant.parse("2031-01-17T01:00:00Z"); // 08:00 ngày 17
        assertThat(StablingPolicy.blocks(runs, morning.minusSeconds(1), HOURS, 30)).isZero();
        assertThat(StablingPolicy.blocks(runs, morning, HOURS, 30)).isEqualTo(1);
        var night = Instant.parse("2031-01-14T20:00:00Z"); // 03:00 ngày 15
        assertThat(StablingPolicy.nextMark(List.of(new Run(night, null)), night, HOURS, 30))
                .contains(Instant.parse("2031-01-16T01:00:00Z"));
    }

    @Test
    void stoppedClockHasNoNextMark() {
        var runs = List.of(new Run(START, START.plus(Duration.ofHours(16))));
        assertThat(StablingPolicy.blocks(runs, START.plus(Duration.ofDays(5)), HOURS, 30)).isZero();
        assertThat(StablingPolicy.nextMark(runs, START.plus(Duration.ofDays(5)), HOURS, 30)).isEmpty();
    }
}
