package com.example.racehorse_transport.policy;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentAmountPolicyTest {

    @Test
    void theMaximumItselfIsAllowed() {
        assertThat(PaymentAmountPolicy.exceedsMaximum(new BigDecimal("9999999999999999"))).isFalse();
    }

    @Test
    void oneDongOverTheMaximumIsRejected() {
        assertThat(PaymentAmountPolicy.exceedsMaximum(new BigDecimal("10000000000000000"))).isTrue();
    }

    @Test
    void amountIsRoundedToWholeDongBeforeComparing() {
        assertThat(PaymentAmountPolicy.exceedsMaximum(new BigDecimal("9999999999999999.4"))).isFalse();
        assertThat(PaymentAmountPolicy.exceedsMaximum(new BigDecimal("9999999999999999.5"))).isTrue();
    }

    @Test
    void missingAmountDoesNotExceed() {
        assertThat(PaymentAmountPolicy.exceedsMaximum(null)).isFalse();
    }
}
