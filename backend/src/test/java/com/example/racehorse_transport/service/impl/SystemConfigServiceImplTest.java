package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.admin.UpdateSystemConfigRequest;
import com.example.racehorse_transport.entity.SystemConfig;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.SystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemConfigServiceImplTest {

    private final SystemConfigRepository repository = mock(SystemConfigRepository.class);
    private final SystemConfigServiceImpl service = new SystemConfigServiceImpl(repository);

    @BeforeEach
    void setUp() {
        when(repository.findAllById(anyIterable())).thenReturn(List.of(
                config("BORDER_OPEN_MINUTES", "450"),
                config("BORDER_CLOSE_MINUTES", "990"),
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
    void updateRejectsBorderOpeningAfterClosing() {
        when(repository.findById("BORDER_CLOSE_MINUTES"))
                .thenReturn(Optional.of(config("BORDER_CLOSE_MINUTES", "990")));

        assertThatThrownBy(() -> service.updateConfig("BORDER_OPEN_MINUTES",
                UpdateSystemConfigRequest.builder().configValue("1000").build(), "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sớm hơn giờ đóng cửa");
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
