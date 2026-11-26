package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.exception.HorseRuleException;
import org.junit.jupiter.api.Test;

class HorsePolicyTest {

    @Test
    void microchipIsNormalizedAndMustHaveFifteenDigits() {
        assertThat(HorsePolicy.requireMicrochip(" 985-000 000000001 ")).isEqualTo("985000000000001");
        for (String wrong : new String[] {null, "", "98500000000000", "98500000000000A"}) {
            assertThatThrownBy(() -> HorsePolicy.requireMicrochip(wrong)).isInstanceOf(HorseRuleException.class);
        }
    }

    @Test
    void birthYearCannotBeInTheFuture() {
        assertThatCode(() -> HorsePolicy.requireBirthYearNotAfter(null, 2026)).doesNotThrowAnyException();
        assertThatCode(() -> HorsePolicy.requireBirthYearNotAfter((short) 2026, 2026)).doesNotThrowAnyException();
        assertThatThrownBy(() -> HorsePolicy.requireBirthYearNotAfter((short) 2027, 2026))
                .isInstanceOf(HorseRuleException.class).hasMessage("Nhập năm sinh hợp lệ.");
    }
}
