package com.example.racehorse_transport.dto.regulation;

import java.time.Instant;
import java.util.List;

public record RegulationPreviewResponseDto(
        CountryRuleResponseDto rule,
        long leadDays,
        List<DocumentRequirementResponseDto> requirements,
        boolean requirementsDependOnGate,
        Instant bookingCutoffAt,
        Boolean feasible) {
}
