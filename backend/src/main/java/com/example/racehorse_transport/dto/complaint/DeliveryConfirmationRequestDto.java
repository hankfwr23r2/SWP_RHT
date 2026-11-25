package com.example.racehorse_transport.dto.complaint;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record DeliveryConfirmationRequestDto(@NotNull @AssertTrue Boolean confirmed) {
}
