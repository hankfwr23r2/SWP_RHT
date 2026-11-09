package com.example.racehorse_transport.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScheduleCancelRequest(@NotBlank @Size(max = 500) String reason) { }
