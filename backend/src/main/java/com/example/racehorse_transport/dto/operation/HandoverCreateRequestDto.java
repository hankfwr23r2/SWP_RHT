package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.HandoverType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// partyId: stable operator for STAY_IN or STAY_OUT
public record HandoverCreateRequestDto(@NotNull HandoverType handoverType, @Size(max = 1000) String note, @Positive Long partyId) {

    // Trạng thái và giờ giao do service đặt
    public Handover toEntity(TripStop stop, Staff handledBy) {
        Handover handover = new Handover();
        handover.setHandoverType(handoverType);
        handover.setTripStop(stop);
        handover.setHandledBy(handledBy);
        handover.setNote(note == null || note.isBlank() ? null : note.strip());
        return handover;
    }
}
