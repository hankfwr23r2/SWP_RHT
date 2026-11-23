package com.example.racehorse_transport.dto.incident;

import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.IncidentPlan;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.enums.IncidentResolutionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record IncidentPlanRequestDto(
        @NotNull IncidentResolutionType resolutionType,
        @NotBlank @Size(max = 4000) String instructions,
        @PositiveOrZero BigDecimal estimatedCost,
        @Positive Long handlerPartyId) {

    // Phiên bản, trạng thái, người lập do service đặt
    public IncidentPlan toEntity(Incident incident, Party handler) {
        IncidentPlan plan = new IncidentPlan();
        plan.setIncident(incident);
        plan.setResolutionType(resolutionType);
        plan.setInstructions(instructions.strip());
        plan.setEstimatedCost(estimatedCost);
        plan.setHandlerParty(handler);
        return plan;
    }
}
