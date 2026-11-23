package com.example.racehorse_transport.dto.departure;

import com.example.racehorse_transport.entity.enums.BookingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

// Tham số tìm chuyến cho đơn mới (query string). Tọa độ dùng để xếp hạng cửa khẩu với đơn quốc tế.
@Getter
@Setter
@Schema(name = "DepartureSuggestionQuery")
public class DepartureSuggestionQueryDto {

    @NotNull(message = "Chọn loại đơn")
    private BookingType bookingType;

    @NotBlank(message = "Chọn tỉnh của điểm đón")
    private String pickupProvinceCode;

    @NotBlank(message = "Chọn tỉnh của điểm giao")
    private String deliveryProvinceCode;

    @DecimalMin("-90") @DecimalMax("90")
    private BigDecimal pickupLatitude;

    @DecimalMin("-180") @DecimalMax("180")
    private BigDecimal pickupLongitude;

    @DecimalMin("-90") @DecimalMax("90")
    private BigDecimal deliveryLatitude;

    @DecimalMin("-180") @DecimalMax("180")
    private BigDecimal deliveryLongitude;

    // Chỉ gợi ý chuyến chạy từ ngày này trở đi
    @NotNull(message = "Chọn ngày muốn đi")
    private LocalDate fromDate;

    @Schema(description = "Số chỗ cần: khoang thường 1 chỗ, khoang rộng 2 chỗ")
    @NotNull
    @Min(value = 1, message = "Cần ít nhất 1 chỗ")
    @Max(value = 100, message = "Tối đa 100 chỗ")
    private Integer stalls = 1;
}
