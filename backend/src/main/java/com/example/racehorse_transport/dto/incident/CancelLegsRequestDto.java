package com.example.racehorse_transport.dto.incident;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CancelLegsRequestDto(
        @NotNull(message = "Chọn phương án hủy chặng.")
        @Positive(message = "Mã phương án phải là số dương.")
        Long incidentPlanId) {}
