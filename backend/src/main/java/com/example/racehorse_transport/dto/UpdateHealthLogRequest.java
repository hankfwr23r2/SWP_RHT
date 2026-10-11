package com.example.racehorse_transport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateHealthLogRequest {
    @NotNull(message = "horseId không được để trống")
    private Integer horseId;

    private Integer bookingId;
    private Integer escortStaffId;

    @NotBlank(message = "Trạng thái sức khỏe không được để trống")
    private String healthStatus;

    private BigDecimal temperature;
    private BigDecimal weight;
    private String symptoms;
}