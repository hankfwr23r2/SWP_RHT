package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.enums.PickupMissChoice;
import com.example.racehorse_transport.entity.enums.PickupMissLiability;
import java.time.Instant;
import java.util.List;

public record PickupMissResponseDto(Long bookingHorseId, String horseName, Instant missedAt, Instant choiceDueAt,
        String reason, PickupMissLiability liability, PickupMissChoice choice, List<PickupMissChoice> availableChoices) {
    public static PickupMissResponseDto from(BookingHorse horse, List<PickupMissChoice> choices) {
        return new PickupMissResponseDto(horse.getId(), horse.getHorse().getName(), horse.getPickupMissedAt(),
                horse.getPickupChoiceDueAt(), horse.getPickupMissReason(), horse.getPickupMissLiability(),
                horse.getPickupMissChoice(), choices);
    }
}
