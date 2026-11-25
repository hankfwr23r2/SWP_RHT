package com.example.racehorse_transport.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookingReassignmentRequestDto(@NotNull Long specialistId, @NotBlank @Size(max = 1000) String reason) {}
