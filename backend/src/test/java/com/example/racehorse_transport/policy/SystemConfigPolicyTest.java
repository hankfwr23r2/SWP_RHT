package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.exception.SystemConfigRuleException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemConfigPolicyTest {

    @Test
    void acceptsPositiveIntegersForTechnicalUnits() {
        assertThatCode(() -> SystemConfigPolicy.requireValidUpdate("MINUTE", 1)).doesNotThrowAnyException();
        assertThatCode(() -> SystemConfigPolicy.requireValidUpdate("COUNT", 5)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedUnitsAndNonpositiveValues() {
        assertThatThrownBy(() -> SystemConfigPolicy.requireValidUpdate("VND", 1)).isInstanceOf(SystemConfigRuleException.class);
        assertThatThrownBy(() -> SystemConfigPolicy.requireValidUpdate(null, 1)).isInstanceOf(SystemConfigRuleException.class);
        assertThatThrownBy(() -> SystemConfigPolicy.requireValidUpdate("DAY", 0)).isInstanceOf(SystemConfigRuleException.class);
    }
}
