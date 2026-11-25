package com.example.racehorse_transport.dto.payment;

import com.example.racehorse_transport.entity.enums.DisputeDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DebtDisputeDecisionRequestDto(@NotNull DisputeDecision decision, @NotBlank @Size(max = 500) String response) {
}
