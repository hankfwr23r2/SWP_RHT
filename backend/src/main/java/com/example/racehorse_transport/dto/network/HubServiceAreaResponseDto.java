package com.example.racehorse_transport.dto.network;

import com.example.racehorse_transport.entity.HubServiceArea;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(name = "HubServiceAreaResponse")
public class HubServiceAreaResponseDto {

    private String provinceCode;
    private String provinceName;
    private Long hubLocationId;
    private String hubCode;
    private String hubName;

    // Service đã nạp hub và province
    public static HubServiceAreaResponseDto from(HubServiceArea area) {
        return HubServiceAreaResponseDto.builder()
                .provinceCode(area.getProvince().getCode())
                .provinceName(area.getProvince().getName())
                .hubLocationId(area.getHub().getId())
                .hubCode(area.getHub().getCode())
                .hubName(area.getHub().getName())
                .build();
    }
}
