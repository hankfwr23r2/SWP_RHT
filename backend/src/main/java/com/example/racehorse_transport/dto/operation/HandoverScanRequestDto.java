package com.example.racehorse_transport.dto.operation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// Quét chip một con trong lần giao nhận, kèm tình trạng lúc giao
public record HandoverScanRequestDto(
        @NotNull @Positive Long horseLegId,
        @NotBlank @Size(max = 30) String microchip,
        @Size(max = 1000) String conditionNote) {}
