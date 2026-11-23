package com.example.racehorse_transport.dto.network;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.LocationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(name = "LocationResponse")
public class LocationResponseDto {

    private Long id;
    private String code;
    private String name;
    private LocationType type;
    private String countryCode;
    private String provinceCode;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer openingMinutes;
    private Integer closingMinutes;
    private String contactPhone;
    private ActiveStatus status;

    public static LocationResponseDto from(Location location) {
        return LocationResponseDto.builder()
                .id(location.getId())
                .code(location.getCode())
                .name(location.getName())
                .type(location.getType())
                .countryCode(location.getCountry().getCode())
                .provinceCode(location.getProvince() == null ? null : location.getProvince().getCode())
                .address(location.getAddress())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .openingMinutes(location.getOpeningMinutes())
                .closingMinutes(location.getClosingMinutes())
                .contactPhone(location.getContactPhone())
                .status(location.getStatus())
                .build();
    }
}
