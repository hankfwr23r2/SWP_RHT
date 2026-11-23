package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SettlementPolicyTest {

    private static BigDecimal outstanding(String total, String costs, String paid, String refunded) {
        return SettlementPolicy.outstanding(new BigDecimal(total), new BigDecimal(costs), new BigDecimal(paid), new BigDecimal(refunded));
    }

    @Test
    void addsCustomerCostsAndRefunds() {
        assertThat(outstanding("10000000", "0", "3700000", "0")).isEqualByComparingTo("6300000");
        assertThat(outstanding("10000000", "500000", "10000000", "0")).isEqualByComparingTo("500000");
        assertThat(outstanding("8000000", "0", "10000000", "2000000")).isEqualByComparingTo("0");
        assertThat(outstanding("8000000", "0", "10000000", "0")).isEqualByComparingTo("-2000000");
    }

    @Test
    void paidInFullWhenNothingIsOwed() {
        assertThat(SettlementPolicy.isPaidInFull(new BigDecimal("0.00"))).isTrue();
        assertThat(SettlementPolicy.isPaidInFull(new BigDecimal("-1"))).isTrue();
        assertThat(SettlementPolicy.isPaidInFull(new BigDecimal("0.01"))).isFalse();
    }
}
