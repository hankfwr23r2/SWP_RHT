package com.example.racehorse_transport.dto.incident;

import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.enums.IncidentSeverity;
import jakarta.validation.constraints.NotNull;

public record IncidentSeverityRequestDto(@NotNull IncidentSeverity severity) {

    public void applyTo(Incident incident) {
        incident.setSeverity(severity);
    }
}
