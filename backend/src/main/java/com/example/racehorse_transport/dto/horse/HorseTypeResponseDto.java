package com.example.racehorse_transport.dto.horse;

import com.example.racehorse_transport.entity.HorseType;

public record HorseTypeResponseDto(Long id, String name, String description) {

    public static HorseTypeResponseDto from(HorseType horseType) {
        return new HorseTypeResponseDto(horseType.getId(), horseType.getName(), horseType.getDescription());
    }
}
