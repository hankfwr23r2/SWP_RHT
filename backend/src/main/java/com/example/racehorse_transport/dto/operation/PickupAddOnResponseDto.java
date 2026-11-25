package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.entity.BookingHorseAddOn;
import java.time.Instant;

public record PickupAddOnResponseDto(Long serviceId, String name, Instant completedAt) {
    public static PickupAddOnResponseDto from(BookingHorseAddOn addOn) {
        return new PickupAddOnResponseDto(addOn.getBillableService().getId(),
                addOn.getBillableService().getName(), addOn.getCompletedAt());
    }
}
