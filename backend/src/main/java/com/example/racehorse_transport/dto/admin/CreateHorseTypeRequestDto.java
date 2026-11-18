package com.example.racehorse_transport.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import com.example.racehorse_transport.entity.HorseType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateHorseTypeRequest")
public class CreateHorseTypeRequestDto {

    @NotBlank(message = "Tên giống ngựa không được để trống")
    @Size(max = 100, message = "Tên giống ngựa không được vượt quá 100 ký tự")
    private String typeName;

    private String description;

    @NotNull(message = "Giá trị định giá bảo hiểm không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Giá trị bảo hiểm phải lớn hơn hoặc bằng 0")
    private BigDecimal insuredValue;

    public HorseType toEntity() {
        HorseType horseType = new HorseType();
        applyTo(horseType);
        return horseType;
    }

    public void applyTo(HorseType horseType) {
        horseType.setTypeName(getTypeName().trim());
        horseType.setDescription(getDescription() != null ? getDescription().trim() : null);
        horseType.setInsuredValue(getInsuredValue());
    }
}
