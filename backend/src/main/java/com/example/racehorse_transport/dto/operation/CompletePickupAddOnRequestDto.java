package com.example.racehorse_transport.dto.operation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CompletePickupAddOnRequestDto(@NotNull @Positive Long serviceId) {}
