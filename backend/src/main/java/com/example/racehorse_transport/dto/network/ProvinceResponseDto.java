package com.example.racehorse_transport.dto.network;

import com.example.racehorse_transport.entity.Province;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(name = "ProvinceResponse")
public class ProvinceResponseDto {

    private String code;
    private String name;
    private String countryCode;

    // Service đã nạp country (hoặc country là proxy chỉ cần id)
    public static ProvinceResponseDto from(Province province) {
        return ProvinceResponseDto.builder()
                .code(province.getCode())
                .name(province.getName())
                .countryCode(province.getCountry().getCode())
                .build();
    }
}
