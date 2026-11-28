package com.example.racehorse_transport.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.policy.DemoTimeRulePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DemoPropertiesTest {

    private static MockEnvironment profiles(String... names) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(names);
        return environment;
    }

    @Test
    void skipsTimeRulesOnlyWithADatabaseOnThisMachine() {
        assertThat(new DemoProperties(true, "jdbc:postgresql://localhost:5432/db", profiles("demo-live")).skipTimeRules()).isTrue();
        assertThat(new DemoProperties(true, "jdbc:postgresql://postgres:5432/db", profiles("demo-live")).skipTimeRules()).isTrue();
        assertThatThrownBy(() -> new DemoProperties(true, "jdbc:postgresql://db.example.com:5432/db", profiles()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new DemoProperties(true, "jdbc:postgresql://localhost:5432/db", profiles("prod")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void normalRunsKeepTimeRulesWhateverTheDatabase() {
        assertThat(new DemoProperties(false, "jdbc:postgresql://db.example.com:5432/db", profiles("prod")).skipTimeRules()).isFalse();
    }

    @Test
    void demoOverridesOnlyBlockingTimeKeys() {
        assertThat(DemoTimeRulePolicy.override("MIN_REST_MINUTES")).hasValue(0);
        assertThat(DemoTimeRulePolicy.override("HUB_CHECKIN_HOURS")).hasValue(0);
        assertThat(DemoTimeRulePolicy.override("PICKUP_WINDOW_END_HOUR")).hasValue(24);
        // Hạn tiền và hạn báo giá giữ nguyên
        assertThat(DemoTimeRulePolicy.override("BALANCE_WAIT_MINUTES")).isEmpty();
        assertThat(DemoTimeRulePolicy.override("QUOTATION_VALID_HOURS")).isEmpty();
    }
}
