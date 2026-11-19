package com.example.racehorse_transport.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ScheduleCancelRequest")
public record ScheduleCancelRequestDto(@NotBlank @Size(max = 500) String reason) { }
