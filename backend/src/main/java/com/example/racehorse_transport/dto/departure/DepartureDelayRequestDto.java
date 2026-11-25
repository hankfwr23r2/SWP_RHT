package com.example.racehorse_transport.dto.departure;

import com.example.racehorse_transport.entity.Departure;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record DepartureDelayRequestDto(@NotNull Instant revisedDepartureAt, @NotBlank @Size(max = 500) String reason) {
    public void applyTo(Departure departure) {
        departure.setRevisedDepartureAt(revisedDepartureAt);
        departure.setDelayReason(reason.strip());
    }
}
