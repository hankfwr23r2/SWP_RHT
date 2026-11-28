package com.example.racehorse_transport.dto.booking;

import jakarta.validation.constraints.NotNull;

public record BookingIntakeRequestDto(@NotNull Long specialistId) {}
