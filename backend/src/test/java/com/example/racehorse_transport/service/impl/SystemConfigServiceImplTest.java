package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.admin.UpdateSystemConfigRequest;
import com.example.racehorse_transport.entity.SystemConfig;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.SystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemConfigServiceImplTest {

    private final SystemConfigRepository repository = mock(SystemConfigRepository.class);
    private final SystemConfigServiceImpl service = new SystemConfigServiceImpl(org.mockito.Mockito.mock(com.example.racehorse_transport.service.AdminAuditWriter.class), repository);

    @BeforeEach
    void setUp() {
        when(repository.findAllById(anyIterable())).thenReturn(List.of(
                config("BORDER_OPEN_MINUTES", "450"),
                config("BORDER_CLOSE_MINUTES", "990"),
                config("INSURANCE_RATE", "0.02"),
                config("DEPOSIT_RATE", "0.30")));
    }

    @Test
    void updateRejectsDepositRateOutsideTheSupportedRange() {
        assertThatThrownBy(() -> service.updateConfig("DEPOSIT_RATE",
                UpdateSystemConfigRequest.builder().configValue("1").build(), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0.01 đến 0.99");
    }

    @Test
    void globalBorderHoursAreNoLongerEditable() {
        assertThatThrownBy(() -> service.updateConfig("BORDER_OPEN_MINUTES",
                UpdateSystemConfigRequest.builder().configValue("480").build(), "admin"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getAllHidesRetiredRulesIncludingInsuranceRate() {
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(
                config("BORDER_OPEN_MINUTES", "450"),
                config("BORDER_CLOSE_MINUTES", "990"),
                config("INSURANCE_RATE", "0.02"),
                config("DEPOSIT_RATE", "0.30")));

        assertThat(service.getAllConfigs()).extracting("configKey").containsExactly("DEPOSIT_RATE");
    }

    @Test
    void legacyInsuranceRateIsNoLongerEditable() {
        assertThatThrownBy(() -> service.updateConfig("INSURANCE_RATE",
                UpdateSystemConfigRequest.builder().configValue("0.05").build(), "admin"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateAcceptsDecimalForMaxContinuousHours() {
        SystemConfig current = config("MAX_CONTINUOUS_HOURS", "4");
        when(repository.findById("MAX_CONTINUOUS_HOURS")).thenReturn(java.util.Optional.of(current));
        when(repository.save(any(SystemConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.example.racehorse_transport.dto.admin.SystemConfigResponse response = service.updateConfig("MAX_CONTINUOUS_HOURS",
                UpdateSystemConfigRequest.builder().configValue("3.5").build(), "admin");

        assertThat(response.getConfigValue()).isEqualTo("3.5");
    }

    @Test
    void updateAcceptsDecimalVehicleClassFactorsAndMargin() {
        when(repository.save(any(SystemConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        for (String[] pair : new String[][] {
                { "CLASS_FACTOR_LIGHT", "1.1" }, { "CLASS_FACTOR_MEDIUM", "1.4" },
                { "CLASS_FACTOR_HEAVY", "1.9" }, { "MARGIN_RATE", "0.05" } }) {
            when(repository.findById(pair[0])).thenReturn(java.util.Optional.of(config(pair[0], "1")));
            var response = service.updateConfig(pair[0],
                    UpdateSystemConfigRequest.builder().configValue(pair[1]).build(), "admin");
            assertThat(response.getConfigValue()).as(pair[0]).isEqualTo(pair[1]);
        }
    }

    @Test
    void updateStillRejectsDecimalMoneyAmounts() {
        assertThatThrownBy(() -> service.updateConfig("KM_TIER_1_RATE",
                UpdateSystemConfigRequest.builder().configValue("50000.5").build(), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("số nguyên");
    }

    @Test
    void updateRejectsUnknownConfigurationKeys() {
        assertThatThrownBy(() -> service.updateConfig("UNSUPPORTED_RULE",
                UpdateSystemConfigRequest.builder().configValue("1").build(), "admin"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static SystemConfig config(String key, String value) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        return config;
    }
}
