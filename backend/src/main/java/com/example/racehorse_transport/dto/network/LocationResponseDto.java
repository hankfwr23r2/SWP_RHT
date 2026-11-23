package com.example.racehorse_transport.dto.network;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.LocationType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(name = "LocationResponse")
public class LocationResponseDto {

    private Long id;
    private String code;
    private String name;
    private LocationType locationType;
    private String countryCode;
    private String counterpartCountryCode;
    private String provinceCode;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime openingTime;
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime closingTime;
    private String contactPhone;
    private ActiveStatus status;

    public static LocationResponseDto from(Location location) {
        return LocationResponseDto.builder()
                .id(location.getId())
                .code(location.getCode())
                .name(location.getName())
                .locationType(location.getLocationType())
                .countryCode(location.getCountry().getCode())
                .counterpartCountryCode(location.getCounterpartCountry() == null ? null : location.getCounterpartCountry().getCode())
                .provinceCode(location.getProvince() == null ? null : location.getProvince().getCode())
                .address(location.getAddress())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .openingTime(location.getOpeningTime())
                .closingTime(location.getClosingTime())
                .contactPhone(location.getContactPhone())
                .status(location.getStatus())
                .build();
    }
}
