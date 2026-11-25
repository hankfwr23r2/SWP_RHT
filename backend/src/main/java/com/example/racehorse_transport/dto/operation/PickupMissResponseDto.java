package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.enums.PickupMissChoice;
import com.example.racehorse_transport.entity.enums.PickupMissLiability;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PickupMissResponseDto(Long bookingHorseId, String horseName, Instant missedAt, Instant choiceDueAt,
        String reason, PickupMissLiability liability, PickupMissChoice choice, List<PickupMissChoice> availableChoices,
        Map<PickupMissChoice, PickupMissPriceResponseDto> prices) {
    public static PickupMissResponseDto from(BookingHorse horse, List<PickupMissChoice> choices,
                                             Map<PickupMissChoice, PickupMissPriceResponseDto> prices) {
        return new PickupMissResponseDto(horse.getId(), horse.getHorse().getName(), horse.getPickupMissedAt(),
                horse.getPickupChoiceDueAt(), horse.getPickupMissReason(), horse.getPickupMissLiability(),
                horse.getPickupMissChoice(), choices, prices);
    }
}
