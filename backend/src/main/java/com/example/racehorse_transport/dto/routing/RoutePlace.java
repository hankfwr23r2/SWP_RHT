package com.example.racehorse_transport.dto.routing;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// Điểm đón hoặc điểm giao: tên hiển thị, tọa độ, mã nước (VN, KH, LA)
public record RoutePlace(
        @NotBlank String name,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
        @NotBlank @Pattern(regexp = "(?i)VN|KH|LA") String country) {
}
