package com.example.racehorse_transport.dto.health;

import com.example.racehorse_transport.entity.HorseStay;
import java.math.BigDecimal;
import java.time.Instant;

public record StablingClockResponseDto(Long stayId, String horseName, boolean running, long elapsedSeconds,
                                       BigDecimal amount, Instant nextChargeAt, Instant readyAt,
                                       Instant collectionExpectedAt, boolean canReady, boolean canCollect) {
    public static StablingClockResponseDto from(HorseStay stay, boolean running, long elapsedSeconds,
            BigDecimal amount, Instant nextChargeAt, boolean canReady, boolean canCollect) {
        return new StablingClockResponseDto(stay.getId(), stay.getBookingHorse().getHorse().getName(), running,
                elapsedSeconds, amount, nextChargeAt, stay.getReadyAt(), stay.getCollectionExpectedAt(), canReady, canCollect);
    }
}
