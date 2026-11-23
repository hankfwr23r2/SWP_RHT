package com.example.racehorse_transport.dto.network;

import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.RouteStop;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(name = "RouteResponse")
public class RouteResponseDto {

    private Long id;
    private String code;
    private String name;
    private Long originLocationId;
    private String originCode;
    private String originName;
    private Long destinationLocationId;
    private String destinationCode;
    private String destinationName;
    private BigDecimal distanceKm;
    private Integer estDurationMinutes;
    private ActiveStatus status;
    // Danh sách tuyến không kèm điểm dừng (null); chi tiết tuyến có điểm dừng theo thứ tự
    private List<RouteStopResponseDto> stops;

    // Service đã nạp origin, destination và (nếu có) điểm dừng kèm location
    public static RouteResponseDto from(Route route, List<RouteStop> stops) {
        return RouteResponseDto.builder()
                .id(route.getId())
                .code(route.getCode())
                .name(route.getName())
                .originLocationId(route.getOrigin().getId())
                .originCode(route.getOrigin().getCode())
                .originName(route.getOrigin().getName())
                .destinationLocationId(route.getDestination().getId())
                .destinationCode(route.getDestination().getCode())
                .destinationName(route.getDestination().getName())
                .distanceKm(route.getDistanceKm())
                .estDurationMinutes(route.getEstDurationMinutes())
                .status(route.getStatus())
                .stops(stops == null ? null : stops.stream().map(RouteStopResponseDto::from).toList())
                .build();
    }
}
