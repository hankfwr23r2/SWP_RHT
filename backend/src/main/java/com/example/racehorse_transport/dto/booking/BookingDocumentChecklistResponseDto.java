package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.dto.regulation.DocumentRequirementResponseDto;
import com.example.racehorse_transport.entity.enums.DocumentSubmitter;
import java.util.List;

public record BookingDocumentChecklistResponseDto(boolean requirementsDependOnGate, List<Item> items,
        List<BookingDocumentResponseDto> documents) {
    public record Item(DocumentRequirementResponseDto requirement, Long bookingHorseId, boolean satisfied,
            DocumentSubmitter submitterType, boolean requiredForQuoting, boolean requiredBeforePickup) {
        public static Item from(DocumentRequirementResponseDto requirement, Long bookingHorseId, boolean satisfied,
                boolean requiredForQuoting, boolean requiredBeforePickup) {
            return new Item(requirement, bookingHorseId, satisfied, requirement.submitterType(),
                    requiredForQuoting, requiredBeforePickup);
        }
    }
}
