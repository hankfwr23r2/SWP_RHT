package com.example.racehorse_transport.dto.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateHorseTypeRequest {

    @NotBlank(message = "Tên giống ngựa không được để trống")
    @Size(max = 100, message = "Tên giống ngựa không được vượt quá 100 ký tự")
    private String typeName;

    private String description;

    @NotNull(message = "Giá trị định giá bảo hiểm không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Giá trị bảo hiểm phải lớn hơn hoặc bằng 0")
    private BigDecimal insuredValue;
}
