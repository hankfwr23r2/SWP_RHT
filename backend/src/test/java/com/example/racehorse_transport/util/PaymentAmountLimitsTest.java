package com.example.racehorse_transport.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAmountLimitsTest {

    @Test
    void theMaximumItselfIsAllowed() {
        assertThat(PaymentAmountLimits.exceedsMaximum(new BigDecimal("9999999999999999"))).isFalse();
    }

    @Test
    void oneDongOverTheMaximumIsRejected() {
        assertThat(PaymentAmountLimits.exceedsMaximum(new BigDecimal("10000000000000000"))).isTrue();
    }

    @Test
    void amountIsRoundedToWholeDongBeforeComparing() {
        assertThat(PaymentAmountLimits.exceedsMaximum(new BigDecimal("9999999999999999.4"))).isFalse();
        assertThat(PaymentAmountLimits.exceedsMaximum(new BigDecimal("9999999999999999.5"))).isTrue();
    }

    @Test
    void missingAmountDoesNotExceed() {
        assertThat(PaymentAmountLimits.exceedsMaximum(null)).isFalse();
    }
}
