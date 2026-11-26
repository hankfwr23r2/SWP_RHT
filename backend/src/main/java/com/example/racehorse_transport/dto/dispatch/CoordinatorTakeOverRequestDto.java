package com.example.racehorse_transport.dto.dispatch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CoordinatorTakeOverRequestDto(@NotBlank @Size(max = 500) String reason) {}
