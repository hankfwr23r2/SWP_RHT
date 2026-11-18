package com.example.racehorse_transport.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SystemConfigResponse")
public class SystemConfigResponseDto {
    private String configKey;
    private String configName;
    private String configValue;
    private String unit;
    private String configGroup;
    private String description;
    private String updatedBy;
    private Instant updatedAt;
}
