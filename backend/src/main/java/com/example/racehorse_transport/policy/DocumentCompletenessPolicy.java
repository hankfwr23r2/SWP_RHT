package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.dto.regulation.DocumentRequirementResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.entity.enums.DocumentSubmitter;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class DocumentCompletenessPolicy {
    private DocumentCompletenessPolicy() {}

    public static LocalDate requiredOn(Booking booking, LocalDate today) {
        LocalDate planned = booking.getDeparture() == null ? booking.getRequestedPickupDate()
                : booking.getDeparture().getDepartureDate();
        // Lịch đã qua (đi trễ) thì giấy phải còn hạn hôm nay
        return planned.isBefore(today) ? today : planned;
    }

    public static boolean satisfies(Document document, DocumentRequirementResponseDto requirement,
            Long bookingId, BookingHorse horse, LocalDate on) {
        return document.getStatus() == DocumentStatus.APPROVED
                && (document.getExpiryDate() == null || !document.getExpiryDate().isBefore(on))
                && matchesOwnerAndType(document, requirement, bookingId, horse)
                && (requirement.ownerType() == DocumentSubject.HORSE || matchesRequirement(document, requirement));
    }

    public static boolean matchesOwnerAndRequirement(Document document, DocumentRequirementResponseDto requirement,
            Long bookingId, BookingHorse horse) {
        return matchesRequirement(document, requirement) && matchesOwnerAndType(document, requirement, bookingId, horse);
    }

    private static boolean matchesRequirement(Document document, DocumentRequirementResponseDto requirement) {
        return document.getDocumentRequirement() != null && requirement.id().equals(document.getDocumentRequirement().getId());
    }

    private static boolean matchesOwnerAndType(Document document, DocumentRequirementResponseDto requirement,
            Long bookingId, BookingHorse horse) {
        if (!requirement.documentTypeId().equals(document.getDocumentType().getId())
                || requirement.ownerType() != document.getDocumentType().getOwnerType()) {
            return false;
        }
        return switch (requirement.ownerType()) {
            case BOOKING -> document.getBooking() != null && bookingId.equals(document.getBooking().getId());
            case HORSE -> horse != null && document.getHorse() != null
                    && horse.getHorse().getId().equals(document.getHorse().getId());
            case BOOKING_HORSE -> horse != null && document.getBookingHorse() != null
                    && horse.getId().equals(document.getBookingHorse().getId());
            default -> false;
        };
    }

    // Mã các loại giấy bắt buộc chưa có bản duyệt còn hạn vào ngày đó
    public static Set<String> missingTypes(Long bookingId, List<BookingHorse> activeHorses,
            List<DocumentRequirementResponseDto> requirements, List<Document> documents, LocalDate on) {
        return requirements.stream()
                .filter(requirement -> !isComplete(bookingId, activeHorses, List.of(requirement), documents, on))
                .map(DocumentRequirementResponseDto::documentTypeCode).collect(Collectors.toSet());
    }

    public static boolean isComplete(Long bookingId, List<BookingHorse> activeHorses,
            List<DocumentRequirementResponseDto> requirements, List<Document> documents, LocalDate on) {
        return isComplete(bookingId, activeHorses, requirements, documents, on, null);
    }

    public static boolean isComplete(Long bookingId, List<BookingHorse> activeHorses,
            List<DocumentRequirementResponseDto> requirements, List<Document> documents, LocalDate on,
            DocumentSubmitter submitter) {
        return requirements.stream().filter(r -> Boolean.TRUE.equals(r.mandatory()))
                .filter(r -> submitter == null || r.submitterType() == submitter)
                .allMatch(r -> switch (r.ownerType()) {
                    case BOOKING -> documents.stream().anyMatch(d -> satisfies(d, r, bookingId, null, on));
                    case HORSE, BOOKING_HORSE -> activeHorses.stream().allMatch(h ->
                            documents.stream().anyMatch(d -> satisfies(d, r, bookingId, h, on)));
                    default -> false;
                });
    }
}
