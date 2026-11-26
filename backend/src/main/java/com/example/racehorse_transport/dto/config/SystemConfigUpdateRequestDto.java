package com.example.racehorse_transport.dto.config;

import com.example.racehorse_transport.entity.SystemConfig;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SystemConfigUpdateRequestDto(
        @NotNull @Digits(integer = 10, fraction = 0) @DecimalMin("-2147483648") @DecimalMax("2147483647")
        BigDecimal configValue) {

    public int integerValue() {
        return configValue.intValueExact();
    }

    public void applyTo(SystemConfig configuration) {
        configuration.setConfigValue(Integer.toString(integerValue()));
    }
}
