package com.example.racehorse_transport.dto.vehicle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VehicleStatusChangeRequestDto(@NotBlank @Size(max = 500) String reason) {
}
