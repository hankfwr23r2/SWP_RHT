package com.example.racehorse_transport.dto.incident;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Đổi xe do sự cố: xe thay thế cho lượt đang chạy
public record VehicleSwapRequestDto(@NotNull @Positive Long vehicleId) {}
