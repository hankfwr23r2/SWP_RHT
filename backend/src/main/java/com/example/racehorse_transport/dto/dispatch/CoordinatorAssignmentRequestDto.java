package com.example.racehorse_transport.dto.dispatch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CoordinatorAssignmentRequestDto(@NotNull @Positive Long coordinatorId, @Size(max = 500) String reason) {}
