package com.example.racehorse_transport.dto.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record VetResultRequestDto(
        @NotBlank(message = "Nhập kết quả thú y.")
        @Size(max = 2000, message = "Kết quả thú y tối đa 2000 ký tự.")
        String note,
        @Positive(message = "Mã phương án phải là số dương.") Long incidentPlanId) {}
