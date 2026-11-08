package com.example.racehorse_transport.dto.admin;

import java.time.Instant;

public record SystemLogResponse(Integer id, String actionType, Integer actorId, String actorName,
        String actorEmail, String actorRole, String targetTable, String targetKey, String targetLabel,
        String description, String oldData, String newData, boolean legacy, Instant createdAt) {}
