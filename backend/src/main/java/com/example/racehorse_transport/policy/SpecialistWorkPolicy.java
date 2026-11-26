package com.example.racehorse_transport.policy;

import static com.example.racehorse_transport.entity.enums.BookingStatus.*;
import static com.example.racehorse_transport.entity.enums.HorseInsuranceStatus.REVISION_REQUESTED;
import static com.example.racehorse_transport.entity.enums.HorseInsuranceStatus.CONFIRMED;

import com.example.racehorse_transport.dto.booking.BookingDocumentChecklistResponseDto;
import com.example.racehorse_transport.dto.booking.BookingDocumentResponseDto;
import com.example.racehorse_transport.dto.specialist.SpecialistWorkResponseDto.*;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import com.example.racehorse_transport.entity.enums.DocumentSubmitter;
import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import java.util.List;
import java.util.Objects;

public final class SpecialistWorkPolicy {
    private SpecialistWorkPolicy() {}

    public static Verification verification(BookingStatus status, BookingDocumentChecklistResponseDto checklist) {
        if (status == PENDING_INTAKE) return Verification.AWAITING_INTAKE;
        if (status == NEEDS_RESUBMISSION) return Verification.WAITING_CUSTOMER;
        var customerItems = checklist.items().stream().filter(item -> item.submitterType() == DocumentSubmitter.CUSTOMER).toList();
        boolean pending = customerItems.stream().anyMatch(item -> item.requiredForQuoting() && !item.satisfied())
                || checklist.documents().stream().anyMatch(document -> document.status() == DocumentStatus.UPLOADED
                    && customerItems.stream().anyMatch(item -> matches(item, document)));
        return status == UNDER_REVIEW || (pending && (status == QUOTING || status == AWAITING_DEPOSIT
                || status == BookingStatus.CONFIRMED)) ? Verification.TO_REVIEW : Verification.REVIEWED;
    }

    public static Legal legal(BookingDocumentChecklistResponseDto checklist) {
        var items = checklist.items().stream()
                .filter(item -> item.submitterType() != DocumentSubmitter.CUSTOMER
                        || (item.requirement() != null && item.requirement().gateLocationId() != null))
                .toList();
        if (items.isEmpty()) return checklist.requirementsDependOnGate() ? Legal.TO_PREPARE : null;
        if (items.stream().allMatch(item -> item.satisfied() || !item.requiredBeforePickup())) return Legal.COMPLETE;
        boolean started = items.stream().anyMatch(item -> item.satisfied()
                || checklist.documents().stream().anyMatch(document -> matches(item, document)));
        return started ? Legal.IN_PROGRESS : Legal.TO_PREPARE;
    }

    public static Insurance insurance(List<HorseInsuranceStatus> statuses) {
        if (statuses.isEmpty()) return null;
        if (statuses.stream().anyMatch(status -> status != CONFIRMED && status != REVISION_REQUESTED)) {
            return Insurance.TO_PROCESS;
        }
        return statuses.contains(REVISION_REQUESTED) ? Insurance.WAITING_CUSTOMER : Insurance.CONFIRMED;
    }

    private static boolean matches(BookingDocumentChecklistResponseDto.Item item,
            BookingDocumentResponseDto document) {
        if (!Objects.equals(item.bookingHorseId(), document.bookingHorseId())) return false;
        if (item.requirement() != null && item.requirement().id().equals(document.requirementId())) return true;
        if (document.horseId() == null) return false;
        if (item.requirement() == null && document.requirementId() != null) return false;
        String type = item.requirement() != null ? item.requirement().documentTypeCode() : item.profileDocumentType().code();
        return type.equals(document.typeCode());
    }
}
