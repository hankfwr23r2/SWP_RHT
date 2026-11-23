package com.example.racehorse_transport.dto.config;

import com.example.racehorse_transport.entity.SystemConfig;
import java.time.Instant;

public record SystemConfigResponseDto(String configKey, String configValue, String unit, String configGroup,
                                      String description, Long updatedByUserId, Instant updatedAt,
                                      Integer minValue, Integer maxValue) {

    public static SystemConfigResponseDto from(SystemConfig configuration) {
        var updatedBy = configuration.getUpdatedBy();
        return new SystemConfigResponseDto(configuration.getConfigKey(), configuration.getConfigValue(),
                configuration.getUnit(), configuration.getConfigGroup(), configuration.getDescription(),
                updatedBy == null ? null : updatedBy.getUserId(), configuration.getUpdatedAt(),
                configuration.getMinValue(), configuration.getMaxValue());
    }
}
