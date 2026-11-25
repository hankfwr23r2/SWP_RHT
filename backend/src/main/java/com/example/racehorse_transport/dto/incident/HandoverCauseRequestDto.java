package com.example.racehorse_transport.dto.incident;

import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HandoverCauseRequestDto(@NotNull Cause cause, @NotBlank @Size(max = 500) String reason) {
    public enum Cause {
        RECIPIENT_ABSENT(IncidentCauseType.RECIPIENT_ABSENT),
        COMPANY_FAULT(IncidentCauseType.COMPANY_FAULT),
        FORCE_MAJEURE(IncidentCauseType.FORCE_MAJEURE);

        private final IncidentCauseType incidentCause;

        Cause(IncidentCauseType incidentCause) {
            this.incidentCause = incidentCause;
        }

        public IncidentCauseType incidentCause() {
            return incidentCause;
        }
    }
}
