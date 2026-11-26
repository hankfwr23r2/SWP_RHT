package com.example.racehorse_transport.dto.config;

import com.example.racehorse_transport.entity.SystemConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SystemTextConfigUpdateRequestDto(@NotBlank @Size(max = 500) String configValue) {

    public String textValue() {
        return configValue.strip();
    }

    public void applyTo(SystemConfig configuration) {
        configuration.setConfigValue(textValue());
    }
}
