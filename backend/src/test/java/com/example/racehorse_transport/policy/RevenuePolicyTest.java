package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.exception.DispatchRuleException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RevenuePolicyTest {

    private static BigDecimal vnd(long amount) {
        return BigDecimal.valueOf(amount);
    }

    @Test
    void activeBookingEarnsAcceptedQuotationWithoutPassThroughPlusPaidExtras() {
        assertThat(RevenuePolicy.bookingRevenue(false, vnd(10_000_000), vnd(1_000_000), vnd(500_000), vnd(3_000_000), vnd(0)))
                .isEqualByComparingTo(vnd(9_500_000));
    }

    @Test
    void cancelledBookingEarnsOnlyWhatTheCompanyKeepsAfterRefundAndPassThrough() {
        assertThat(RevenuePolicy.bookingRevenue(true, vnd(10_000_000), vnd(500_000), vnd(0), vnd(3_000_000), vnd(1_000_000)))
                .isEqualByComparingTo(vnd(1_500_000));
        assertThat(RevenuePolicy.bookingRevenue(true, vnd(10_000_000), vnd(500_000), vnd(0), vnd(3_000_000), vnd(3_000_000)))
                .isEqualByComparingTo(vnd(0));
    }

    @Test
    void rangeMustBeForwardAndAtMostOneYear() {
        Instant from = Instant.parse("2031-01-01T00:00:00Z");
        assertThatThrownBy(() -> RevenuePolicy.requireRange(from, from)).isInstanceOf(DispatchRuleException.class);
        assertThatThrownBy(() -> RevenuePolicy.requireRange(from, from.plus(Duration.ofDays(367)))).isInstanceOf(DispatchRuleException.class);
        RevenuePolicy.requireRange(from, from.plus(RevenuePolicy.MAX_RANGE));
    }
}
