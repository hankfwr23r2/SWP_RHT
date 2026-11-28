package com.example.racehorse_transport.dto.operation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PickupWaitExtensionRequestDto(@NotBlank @Size(max = 500) String reason) {}
