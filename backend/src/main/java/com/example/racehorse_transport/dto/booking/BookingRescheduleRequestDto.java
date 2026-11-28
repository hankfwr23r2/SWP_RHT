package com.example.racehorse_transport.dto.booking;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record BookingRescheduleRequestDto(@NotNull @Positive Long departureId, @NotNull LocalDate requestedPickupDate) {}
