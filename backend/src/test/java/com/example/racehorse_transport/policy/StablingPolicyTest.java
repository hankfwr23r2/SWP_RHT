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
    void readyOnTuesdayAndWaitingForCompanyUntilThursdayCostsNothing() {
        var monday = Instant.parse("2031-01-13T11:00:00Z");
        var tuesdayReady = Instant.parse("2031-01-14T03:00:00Z");
        var thursday = Instant.parse("2031-01-16T07:00:00Z");
        assertClock(StablingPolicy.clockRuns(monday, thursday, tuesdayReady, null, null, null, thursday),
                thursday, 16, "0");
    }

    @Test
    void choosingThursdayInsteadOfTuesdayAddsFortyEightHours() {
        var monday = Instant.parse("2031-01-13T11:00:00Z");
        var ready = Instant.parse("2031-01-14T03:00:00Z");
        var earliest = Instant.parse("2031-01-14T07:00:00Z");
        var chosen = Instant.parse("2031-01-16T07:00:00Z");
        assertClock(StablingPolicy.clockRuns(monday, chosen, ready, earliest, null, null, chosen),
                chosen, 64, "2000000");
    }

    @Test
    void twoMissedDeliveriesOfTwentyHoursShareTheSameClock() {
        var firstEnd = START.plus(Duration.ofHours(20));
        var secondStart = START.plus(Duration.ofDays(2));
        var secondEnd = secondStart.plus(Duration.ofHours(20));
        var first = StablingPolicy.clockRuns(START, firstEnd, firstEnd, null, null, null, secondEnd);
        var second = StablingPolicy.clockRuns(secondStart, secondEnd, secondEnd, null, null, null, secondEnd);
        assertClock(java.util.stream.Stream.concat(first.stream(), second.stream()).toList(), secondEnd, 40, "1000000");
    }

    @Test
    void companyTruckTwoHoursLateDoesNotStartAClock() {
        var arrival = START.plus(Duration.ofHours(2));
        assertThat(com.example.racehorse_transport.policy.DeliveryEtaPolicy.companyFault(START, arrival, 60)).isTrue();
        assertClock(StablingPolicy.clockRuns(null, null, null, null, null, null, arrival), arrival, 0, "0");
    }

    @Test
    void hubCollectionUsesReportTimeOnlyIfTheCustomerMakesTheAppointment() {
        var reported = START.plus(Duration.ofHours(20));
        var deadline = START.plus(Duration.ofHours(30));
        assertClock(StablingPolicy.clockRuns(START, deadline, null, null, reported, deadline, deadline),
                deadline, 20, "0");
        var missed = deadline.plus(Duration.ofHours(1));
        assertClock(StablingPolicy.clockRuns(START, null, null, null, reported, deadline, missed), missed, 31, "1000000");
    }

    @Test
    void stopsAtActualPaymentRatherThanWhenTheCustomerDeclaredReadiness() {
        var paid = START.plus(Duration.ofHours(27));
        var delivered = START.plus(Duration.ofDays(4));
        assertClock(StablingPolicy.clockRuns(START, delivered, paid, null, null, null, delivered),
                delivered, 27, "1000000");
    }

    @Test
    void waitingForTheCompanyToIssueRedeliveryDoesNotCountAfterItsInvoiceIsPaid() {
        var ready = START.plus(Duration.ofHours(16));
        var invoice = ready.plus(Duration.ofDays(2));
        var paid = invoice.plus(Duration.ofHours(4));
        var runs = StablingPolicy.clockRuns(START, null, paid, null, null, null, paid);
        assertClock(StablingPolicy.excludePause(runs, new Run(ready, invoice)), paid, 20, "0");
    }

    @Test
    void plannedStayUsesTheQuotationDayRule() {
        var departure = START.plus(Duration.ofDays(3));
        assertThat(StablingPolicy.plannedFee(START, departure, HOURS.zone(), new BigDecimal("800000")))
                .isEqualByComparingTo("1600000");
    }

    private static void assertClock(List<Run> runs, Instant through, long hours, String amount) {
        assertThat(StablingPolicy.elapsed(runs, through)).isEqualTo(Duration.ofHours(hours));
        assertThat(StablingPolicy.fee(StablingPolicy.blocks(runs, through, HOURS, 30), new BigDecimal("1000000")))
                .isEqualByComparingTo(amount);
    }

    @Test
    void stoppedClockHasNoNextMark() {
        var runs = List.of(new Run(START, START.plus(Duration.ofHours(16))));
        assertThat(StablingPolicy.blocks(runs, START.plus(Duration.ofDays(5)), HOURS, 30)).isZero();
        assertThat(StablingPolicy.nextMark(runs, START.plus(Duration.ofDays(5)), HOURS, 30)).isEmpty();
    }
}
