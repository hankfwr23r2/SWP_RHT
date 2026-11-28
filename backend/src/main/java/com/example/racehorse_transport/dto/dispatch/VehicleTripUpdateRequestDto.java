package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.VehicleTrip;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record VehicleTripUpdateRequestDto(
        @Positive Long vehicleId,
        Instant plannedStartAt,
        @Size(min = 1, max = 50) List<@NotNull @Valid StopTimeRequestDto> stops) {

    public record StopTimeRequestDto(@NotNull @Positive Long stopId,
                                     @NotNull Instant plannedArrivalAt, @NotNull Instant plannedDepartureAt) {}

    public void applyTo(VehicleTrip trip, Vehicle vehicle, Instant start, Instant end) {
        trip.setVehicle(vehicle);
        trip.setPlannedStartAt(start);
        trip.setPlannedEndAt(end);
    }
}
