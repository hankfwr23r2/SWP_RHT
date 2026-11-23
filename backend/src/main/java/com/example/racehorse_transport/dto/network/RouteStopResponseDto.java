package com.example.racehorse_transport.dto.network;

import com.example.racehorse_transport.entity.RouteStop;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.RouteStopType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(name = "RouteStopResponse")
public class RouteStopResponseDto {

    private Integer seq;
    private Long locationId;
    private String locationCode;
    private String locationName;
    private LocationType locationType;
    private RouteStopType stopType;
    private Integer offsetMinutes;
    private Integer dwellMinutes;

    // Service đã nạp location
    public static RouteStopResponseDto from(RouteStop stop) {
        return RouteStopResponseDto.builder()
                .seq(stop.getId().getSeq())
                .locationId(stop.getLocation().getId())
                .locationCode(stop.getLocation().getCode())
                .locationName(stop.getLocation().getName())
                .locationType(stop.getLocation().getType())
                .stopType(stop.getStopType())
                .offsetMinutes(stop.getOffsetMinutes())
                .dwellMinutes(stop.getDwellMinutes())
                .build();
    }
}
