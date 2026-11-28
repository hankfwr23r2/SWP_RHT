package com.example.racehorse_transport.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DebtDisputeRequestDto(@NotBlank @Size(max = 500) String reason) {
}
