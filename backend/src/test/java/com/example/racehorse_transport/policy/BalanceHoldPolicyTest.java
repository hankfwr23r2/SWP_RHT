package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class BalanceHoldPolicyTest {
    private final Instant departure = Instant.parse("2026-12-01T08:00:00Z");

    @Test
    void aPendingGatewaySessionCanDelayTheLoadingCutoffForAtMostFiveMinutes() {
        Instant cutoff = departure.minus(1, ChronoUnit.HOURS);
        assertThat(BalanceHoldPolicy.waitingForGateway(cutoff, cutoff, false, 5)).isFalse();
        assertThat(BalanceHoldPolicy.waitingForGateway(cutoff.plusSeconds(299), cutoff, true, 5)).isTrue();
        assertThat(BalanceHoldPolicy.waitingForGateway(cutoff.plusSeconds(300), cutoff, true, 5)).isFalse();
    }

    @Test
    void threeDayDeadlineStartsAtDepartureAndIncludesItsBoundary() {
        Instant deadline = departure.plus(3, ChronoUnit.DAYS);
        assertThat(BalanceHoldPolicy.reached(deadline.minusSeconds(1), departure, 3, ChronoUnit.DAYS)).isFalse();
        assertThat(BalanceHoldPolicy.reached(deadline, departure, 3, ChronoUnit.DAYS)).isTrue();
    }

    @Test
    void earlierDepartureDoesNotConsumeSlackAndDirectRideUsesTheDailyLimit() {
        assertThat(BalanceHoldPolicy.delay(departure, departure.minusSeconds(1))).isZero();
        assertThat(BalanceHoldPolicy.delay(departure, departure.plus(30, ChronoUnit.MINUTES))).hasMinutes(30);
        assertThat(BalanceHoldPolicy.rideLimitSeconds(false, 10)).isEqualTo(18_000);
        assertThat(BalanceHoldPolicy.rideLimitSeconds(true, 10)).isEqualTo(36_000);
    }

    @Test
    void chargesOnlyDaysInCustodyFromDepartureAndCreditsDepositOnceAcrossHorses() {
        assertThat(StablingPolicy.holdDays(departure, departure.minusSeconds(1))).isZero();
        var days = StablingPolicy.holdDays(departure, departure.plus(3, ChronoUnit.DAYS));
        var charge = StablingPolicy.feeForDays(days, 1_000_000);
        var deposit = new BigDecimal("8761000");
        var firstCredit = StablingPolicy.depositCredit(charge, deposit, BigDecimal.ZERO);
        var secondCredit = StablingPolicy.depositCredit(charge, deposit, firstCredit);
        assertThat(firstCredit.add(secondCredit)).isEqualByComparingTo("6000000");
        var sevenDayFee = StablingPolicy.feeForDays(StablingPolicy.holdDays(departure, departure.plus(7, ChronoUnit.DAYS)), 1_000_000);
        assertThat(sevenDayFee.multiply(BigDecimal.valueOf(2))).isEqualByComparingTo("14000000");
        assertThat(StablingPolicy.depositCredit(sevenDayFee, deposit, firstCredit.add(secondCredit)))
                .isEqualByComparingTo("2761000");
    }
}
