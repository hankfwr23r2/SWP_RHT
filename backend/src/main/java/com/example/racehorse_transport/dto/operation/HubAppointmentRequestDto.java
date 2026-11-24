package com.example.racehorse_transport.dto.operation;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

// Giờ hẹn khách tự chở ngựa tới hub (pickup_method SELF)
public record HubAppointmentRequestDto(@NotNull Instant plannedStartAt) {}
