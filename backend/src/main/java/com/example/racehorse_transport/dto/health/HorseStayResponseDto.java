package com.example.racehorse_transport.dto.health;

import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import java.time.Instant;

public record HorseStayResponseDto(Long id, Long bookingHorseId, String bookingCode, String horseName, HorseStayType stayType,
                                   Long locationId, String locationName, Instant plannedEndAt, Instant actualStartAt, Instant actualEndAt) {

    public static HorseStayResponseDto from(HorseStay stay) {
        var line = stay.getBookingHorse();
        return new HorseStayResponseDto(stay.getId(), line.getId(), line.getBooking().getCode(), line.getHorse().getName(), stay.getStayType(),
                stay.getLocation().getId(), stay.getLocation().getName(), stay.getPlannedEndAt(), stay.getActualStartAt(), stay.getActualEndAt());
    }
}
