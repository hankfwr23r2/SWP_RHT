package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.entity.Booking;

public record CollectionCodeResponseDto(String collectionCode, boolean customerConfirmed) {
    public static CollectionCodeResponseDto from(Booking booking) {
        return new CollectionCodeResponseDto(booking.getCollectionCode(), booking.isCollectionConfirmed());
    }

    @Override
    public String toString() {
        return "CollectionCodeResponseDto";
    }
}
