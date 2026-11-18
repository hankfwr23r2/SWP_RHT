package com.example.racehorse_transport.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
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
@Schema(name = "UpdateHorseTypeInsuredValueRequest")
public class UpdateHorseTypeInsuredValueRequestDto {

    @NotNull(message = "Giá trị bảo hiểm không được để trống")
    @DecimalMin(value = "0.00", inclusive = true, message = "Giá trị bảo hiểm không được âm")
    private BigDecimal insuredValue;
}
