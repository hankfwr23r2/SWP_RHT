package com.example.racehorse_transport.dto.booking;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BookingDepartureChangeRequestDto(@NotNull @Positive Long departureId) {}
