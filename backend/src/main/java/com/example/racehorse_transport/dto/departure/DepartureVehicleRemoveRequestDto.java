package com.example.racehorse_transport.dto.departure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartureVehicleRemoveRequestDto(@NotBlank @Size(max = 500) String reason) {
}
