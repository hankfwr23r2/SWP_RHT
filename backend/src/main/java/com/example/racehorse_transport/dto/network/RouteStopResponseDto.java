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

    private Integer sequenceNumber;
    private Long locationId;
    private String locationCode;
    private String locationName;
    private LocationType locationType;
    private RouteStopType stopType;
    private Integer arrivalOffsetMinutes;
    private Integer dwellMinutes;

    // Service đã nạp location
    public static RouteStopResponseDto from(RouteStop stop) {
        return RouteStopResponseDto.builder()
                .sequenceNumber(stop.getId().getSequenceNumber())
                .locationId(stop.getLocation().getId())
                .locationCode(stop.getLocation().getCode())
                .locationName(stop.getLocation().getName())
                .locationType(stop.getLocation().getLocationType())
                .stopType(stop.getStopType())
                .arrivalOffsetMinutes(stop.getArrivalOffsetMinutes())
                .dwellMinutes(stop.getDwellMinutes())
                .build();
    }
}
