package com.example.racehorse_transport.dto.route;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    private String routeName;

    private String description;

    private Integer estimatedTime; // Số giờ ước tính

    private BigDecimal distance; // Cự ly tiêu chuẩn (km)

    private String status; // ACTIVE, INACTIVE

    private List<RouteStopItem> stops;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteStopItem implements Serializable {
        @NotNull(message = "locationId không được để trống")
        private Integer locationId;

        private Integer stopOrder;
    }
}
