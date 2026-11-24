package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.StallType;

// seats: số chỗ loại khoang chiếm trên chuyến, service tính
public record StallTypeResponseDto(String code, String name, int seats) {

    public static StallTypeResponseDto from(StallType stallType, int seats) {
        return new StallTypeResponseDto(stallType.getCode(), stallType.getName(), seats);
    }
}
