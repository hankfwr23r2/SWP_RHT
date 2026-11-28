package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.entity.enums.PickupMissLiability;
import jakarta.validation.constraints.NotNull;

public record PickupMissLiabilityRequestDto(@NotNull PickupMissLiability liability) {}
