package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.policy.StallSeatPolicy;

// Một con ngựa trên lượt xe: chặng, đơn, khoang và số chỗ chiếm
public record TripHorseResponseDto(String bookingCode, String horseName, String microchip, String stallTypeCode, Integer seats,
                                   HorseLegResponseDto leg, String dietNote, Short feedingsPerDay, Boolean feedSuppliedByOwner) {

    public static TripHorseResponseDto from(HorseLeg leg) {
        var line = leg.getBookingHorse();
        return new TripHorseResponseDto(line.getBooking().getCode(), line.getHorse().getName(), line.getHorse().getMicrochip(),
                line.getStallType().getCode(), StallSeatPolicy.seats(line.getStallType().getCode()), HorseLegResponseDto.from(leg),
                line.getDietNote(), line.getFeedingsPerDay(), line.getFeedSuppliedByOwner());
    }
}
