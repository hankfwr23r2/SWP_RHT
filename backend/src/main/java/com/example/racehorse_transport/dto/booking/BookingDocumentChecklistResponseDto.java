package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.dto.regulation.DocumentRequirementResponseDto;
import com.example.racehorse_transport.dto.regulation.DocumentTypeResponseDto;
import com.example.racehorse_transport.entity.DocumentType;
import com.example.racehorse_transport.entity.enums.DocumentSubmitter;
import java.util.List;

public record BookingDocumentChecklistResponseDto(boolean requirementsDependOnGate, List<Item> items,
        List<BookingDocumentResponseDto> documents) {
    public record Item(DocumentRequirementResponseDto requirement, Long bookingHorseId, boolean satisfied,
            DocumentSubmitter submitterType, boolean requiredForQuoting, boolean requiredBeforePickup,
            DocumentTypeResponseDto profileDocumentType) {
        public static Item from(DocumentRequirementResponseDto requirement, Long bookingHorseId, boolean satisfied,
                boolean requiredForQuoting, boolean requiredBeforePickup) {
            return new Item(requirement, bookingHorseId, satisfied, requirement.submitterType(),
                    requiredForQuoting, requiredBeforePickup, null);
        }

        public static Item from(DocumentType type, Long bookingHorseId) {
            return new Item(null, bookingHorseId, false, DocumentSubmitter.CUSTOMER, false, false,
                    DocumentTypeResponseDto.from(type));
        }
    }
}
