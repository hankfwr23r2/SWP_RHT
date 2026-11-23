package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DepositPolicyTest {

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void depositIncludesWholePassThrough() {
        // 30% của 9.000.000 cước + 1.000.000 thu hộ
        assertThat(DepositPolicy.deposit(money("0.3"), money("10000000"), money("1000000"))).isEqualByComparingTo("3700000");
        assertThat(DepositPolicy.deposit(money("0.3"), money("1000000"), money("1000000"))).isEqualByComparingTo("1000000");
        assertThat(DepositPolicy.deposit(money("0"), money("500"), money("0"))).isEqualByComparingTo("0");
        assertThat(DepositPolicy.deposit(money("1"), money("500"), money("100"))).isEqualByComparingTo("500");
    }

    @Test
    void roundsUpToThousandDong() {
        // 0.3 x 10.000.001 = 3.000.000,3
        assertThat(DepositPolicy.deposit(money("0.3"), money("10000001"), money("0"))).isEqualByComparingTo("3001000");
        assertThat(DepositPolicy.deposit(money("0.3"), money("10000000"), money("0"))).isEqualByComparingTo("3000000");
        // 0.3 x 1.102.500 = 330.750
        assertThat(DepositPolicy.deposit(money("0.3"), money("1102500"), money("0"))).isEqualByComparingTo("331000");
    }

    @Test
    void neverExceedsTotal() {
        assertThat(DepositPolicy.deposit(money("1"), money("1500"), money("0"))).isEqualByComparingTo("1500");
        assertThat(DepositPolicy.deposit(money("0.3"), money("500"), money("100"))).isEqualByComparingTo("500");
    }

    @Test
    void rejectsInvalidRateAndPassThrough() {
        assertThatThrownBy(() -> DepositPolicy.deposit(money("1.01"), money("1"), money("0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DepositPolicy.deposit(money("-0.1"), money("1"), money("0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DepositPolicy.deposit(money("0.3"), money("1"), money("2"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DepositPolicy.deposit(money("0.3"), money("1"), money("-1"))).isInstanceOf(IllegalArgumentException.class);
    }
}
