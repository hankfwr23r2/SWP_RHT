package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.exception.SystemConfigRuleException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemConfigPolicyTest {

    @Test
    void textSettingsOnlyAcceptTextUnitAndAPhoneNumberForHotline() {
        SystemConfigPolicy.requireValidText("HOTLINE", "TEXT", "1900 6868");
        SystemConfigPolicy.requireValidText("HOTLINE", "TEXT", "+84 28 3822 1234");
        SystemConfigPolicy.requireValidText("OFFICE_ADDRESS", "TEXT", "12 Nguyễn Huệ");
        assertThatThrownBy(() -> SystemConfigPolicy.requireValidText("HOTLINE", "TEXT", "abc"))
                .isInstanceOf(SystemConfigRuleException.class);
        assertThatThrownBy(() -> SystemConfigPolicy.requireValidText("OTP_MAX_ATTEMPTS", "COUNT", "5"))
                .isInstanceOf(SystemConfigRuleException.class);
    }

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

    @Test
    void fleetPercentagesAndRestHoursUseTheirConfiguredBounds() {
        assertThatCode(() -> SystemConfigPolicy.requireValidUpdate("PERCENT", 120, 100, 1000)).doesNotThrowAnyException();
        assertThatCode(() -> SystemConfigPolicy.requireValidUpdate("PERCENT", 0, 0, 100)).doesNotThrowAnyException();
        assertThatCode(() -> SystemConfigPolicy.requireValidUpdate("HOURS", 12, 1, 48)).doesNotThrowAnyException();
        assertThatThrownBy(() -> SystemConfigPolicy.requireValidUpdate("PERCENT", 101, 0, 100))
                .isInstanceOf(SystemConfigRuleException.class).hasMessageContaining("từ 0 đến 100");
    }
}
