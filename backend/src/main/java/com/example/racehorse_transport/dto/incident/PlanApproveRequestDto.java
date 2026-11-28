package com.example.racehorse_transport.dto.incident;

import com.example.racehorse_transport.entity.enums.IncidentCauseType;

public record PlanApproveRequestDto(IncidentCauseType confirmedCauseType) {}
