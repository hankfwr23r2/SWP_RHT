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
public class CreateServiceRequest {

    @NotBlank(message = "Tên dịch vụ không được để trống")
    @Size(max = 255, message = "Tên dịch vụ không được vượt quá 255 ký tự")
    private String serviceName;

    @NotBlank(message = "Mã dịch vụ không được để trống")
    @Size(max = 100, message = "Mã dịch vụ không được vượt quá 100 ký tự")
    @jakarta.validation.constraints.Pattern(
            regexp = "^[A-Z][A-Z0-9_]{1,99}$",
            message = "Mã dịch vụ phải viết hoa, bắt đầu bằng chữ cái và chỉ gồm chữ, số, dấu gạch dưới")
    private String serviceCode;

    @NotBlank(message = "Đơn vị tính không được để trống")
    @jakarta.validation.constraints.Pattern(
            regexp = "PER_TRIP|PER_HORSE|PER_HORSE_DAY",
            message = "Đơn vị tính không hợp lệ")
    private String billingUnit;

    @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
    private String description;

    @NotNull(message = "CategoryID không được để trống")
    private Integer categoryId;

    @NotNull(message = "Đơn giá không được để trống")
    @DecimalMin(value = "0.00", inclusive = true, message = "Đơn giá phải lớn hơn hoặc bằng 0")
    private BigDecimal price;

    private String status;
}
