package com.example.racehorse_transport.dto.health;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.HealthCheckType;
import jakarta.validation.constraints.*;
import java.time.Instant;

public record HealthCheckTaskCreateRequestDto(
        @NotNull @Positive Long bookingHorseId, @Positive Long vehicleTripId, @Positive Long horseStayId,
        @NotNull @Positive Long assignedTo, @NotNull HealthCheckType checkType,
        @NotNull Instant dueAt, @Size(max = 500) String note) {
    public HealthCheckTask toEntity(BookingHorse horse, VehicleTrip trip, HorseStay stay, Staff escort) {
        HealthCheckTask task = new HealthCheckTask();
        task.setBookingHorse(horse);
        task.setVehicleTrip(trip);
        task.setHorseStay(stay);
        task.setAssignedTo(escort);
        task.setCheckType(checkType);
        task.setDueAt(dueAt);
        task.setNote(note);
        return task;
    }
}
