package com.example.racehorse_transport.dto.route;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRouteRequest implements Serializable {

    @NotBlank(message = "Tên tuyến đường không được để trống")
    @Size(max = 255, message = "Tên tuyến đường không được vượt quá 255 ký tự")
    private String routeName;

    @Size(max = 5000, message = "Mô tả tuyến đường không được vượt quá 5000 ký tự")
    private String description;

    @Positive(message = "Thời gian di chuyển phải lớn hơn 0")
    private Integer estimatedTime; // Số giờ ước tính

    @DecimalMin(value = "0.0", message = "Cự ly phải lớn hơn hoặc bằng 0")
    private BigDecimal distance; // Cự ly tiêu chuẩn (km)

    @Pattern(regexp = "ACTIVE|MAINTENANCE|INACTIVE", flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "Trạng thái tuyến phải là ACTIVE, MAINTENANCE hoặc INACTIVE")
    private String status; // ACTIVE, MAINTENANCE, INACTIVE

    @Valid
    private List<RouteStopItem> stops;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteStopItem implements Serializable {
        @NotNull(message = "locationId không được để trống")
        private Integer locationId;

        @Positive(message = "Thứ tự điểm dừng phải lớn hơn 0")
        private Integer stopOrder;

        private Double latitude;

        private Double longitude;
    }
}
