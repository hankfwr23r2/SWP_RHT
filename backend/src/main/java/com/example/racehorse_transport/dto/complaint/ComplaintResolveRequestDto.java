package com.example.racehorse_transport.dto.complaint;

import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.enums.ComplaintDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ComplaintResolveRequestDto(@NotNull ComplaintDecision decision,
        @NotBlank @Size(max = 4000) String reason) {

    public void applyTo(Incident complaint) {
        complaint.setComplaintDecisionReason(reason.strip());
    }
}
