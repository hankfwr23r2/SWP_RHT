package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.exception.SystemConfigRuleException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemConfigPolicyTest {

    @Test
    void acceptsPositiveIntegersForTechnicalUnits() {
        assertThatCode(() -> SystemConfigPolicy.requireValidUpdate("MINUTE", 1, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> SystemConfigPolicy.requireValidUpdate("COUNT", 5, null, null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedUnitsAndNonpositiveValues() {
        assertThatThrownBy(() -> SystemConfigPolicy.requireValidUpdate("VND", 1, null, null))
                .isInstanceOf(SystemConfigRuleException.class);
        assertThatThrownBy(() -> SystemConfigPolicy.requireValidUpdate(null, 1, null, null))
                .isInstanceOf(SystemConfigRuleException.class);
        assertThatThrownBy(() -> SystemConfigPolicy.requireValidUpdate("DAY", 0, null, null))
                .isInstanceOf(SystemConfigRuleException.class);
    }

    @Test
    void acceptsBothBoundsAndRejectsValuesOutsideThem() {
        assertThatCode(() -> SystemConfigPolicy.requireValidUpdate("COUNT", 1, 1, 10)).doesNotThrowAnyException();
        assertThatCode(() -> SystemConfigPolicy.requireValidUpdate("COUNT", 10, 1, 10)).doesNotThrowAnyException();
        assertThatThrownBy(() -> SystemConfigPolicy.requireValidUpdate("COUNT", 0, 1, 10))
                .isInstanceOf(SystemConfigRuleException.class).hasMessageContaining("từ 1 đến 10");
        assertThatThrownBy(() -> SystemConfigPolicy.requireValidUpdate("COUNT", 11, 1, 10))
                .isInstanceOf(SystemConfigRuleException.class).hasMessageContaining("từ 1 đến 10");
    }
}
