package com.example.racehorse_transport.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "SystemLogResponse")
public record SystemLogResponseDto(Integer id, String actionType, Integer actorId, String actorName,
        String actorEmail, String actorRole, String targetTable, String targetKey, String targetLabel,
        String description, String oldData, String newData, boolean legacy, Instant createdAt) {}
