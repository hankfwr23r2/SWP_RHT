package com.example.racehorse_transport.dto.incident;

import com.example.racehorse_transport.entity.Incident;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TripUnlockRequestDto(@NotBlank @Size(max = 1000) String reason) {

    public void applyTo(Incident incident) {
        incident.setTripUnlockReason(reason.strip());
    }
}
