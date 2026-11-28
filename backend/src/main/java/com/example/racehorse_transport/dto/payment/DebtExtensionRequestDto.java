package com.example.racehorse_transport.dto.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DebtExtensionRequestDto(@NotNull @Positive Integer days) {
}
