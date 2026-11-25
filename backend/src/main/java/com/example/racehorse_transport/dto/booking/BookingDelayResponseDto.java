package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.Departure;
import java.time.Instant;

public record BookingDelayResponseDto(Long departureId, Instant plannedDepartureAt, Instant revisedDepartureAt,
                                      String delayReason, boolean autoCancelled, boolean canRescheduleFree,
                                      boolean canRefundFull, boolean requiresManagerContact) {
    public static BookingDelayResponseDto from(Departure departure, boolean canRescheduleFree, boolean canRefundFull,
                                               boolean requiresManagerContact) {
        return new BookingDelayResponseDto(departure.getId(), departure.getPlannedDepartureAt(), departure.getRevisedDepartureAt(),
                departure.getDelayReason(), departure.getAutoCancelledAt() != null,
                canRescheduleFree, canRefundFull, requiresManagerContact);
    }
}
