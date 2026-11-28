package com.example.racehorse_transport.dto.operation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BalanceHoldContactRequestDto(@NotNull Target target, @NotBlank @Size(max = 500) String result) {
    public enum Target { CUSTOMER, EMERGENCY_CONTACT }
}
