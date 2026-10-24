package com.example.racehorse_transport.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfigResponse {
    private String configKey;
    private String configName;
    private String configValue;
    private String unit;
    private String configGroup;
    private String description;
    private String updatedBy;
    private Instant updatedAt;
}
