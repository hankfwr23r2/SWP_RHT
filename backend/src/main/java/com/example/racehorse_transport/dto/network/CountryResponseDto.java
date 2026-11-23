package com.example.racehorse_transport.dto.network;

import com.example.racehorse_transport.entity.Country;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(name = "CountryResponse")
public class CountryResponseDto {

    private String code;
    private String name;
    private String defaultCurrency;

    public static CountryResponseDto from(Country country) {
        return CountryResponseDto.builder()
                .code(country.getCode())
                .name(country.getName())
                .defaultCurrency(country.getDefaultCurrency())
                .build();
    }
}
