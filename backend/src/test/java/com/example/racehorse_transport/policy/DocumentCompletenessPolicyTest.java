package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import com.example.racehorse_transport.dto.regulation.DocumentRequirementResponseDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentCompletenessPolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    @Test
    void requiresSeparateBookingHorseDocumentsAndOneBookingDocument() {
        BookingHorse first = horse(11L);
        BookingHorse second = horse(12L);
        var perHorse = requirement(1L, 10L, DocumentSubject.BOOKING_HORSE);
        var perBooking = requirement(2L, 20L, DocumentSubject.BOOKING);
        Document firstDoc = document(perHorse);
        firstDoc.setBookingHorse(first);
        Document secondDoc = document(perHorse);
        secondDoc.setBookingHorse(second);
        Document bookingDoc = document(perBooking);
        Booking booking = new Booking();
        booking.setId(100L);
        bookingDoc.setBooking(booking);

        List<BookingHorse> horses = List.of(first, second);
        List<DocumentRequirementResponseDto> requirements = List.of(perHorse, perBooking);
        assertThat(DocumentCompletenessPolicy.isComplete(100L, horses, requirements, List.of(firstDoc, secondDoc), TODAY)).isFalse();
        assertThat(DocumentCompletenessPolicy.isComplete(100L, horses, requirements, List.of(firstDoc, bookingDoc), TODAY)).isFalse();
        assertThat(DocumentCompletenessPolicy.isComplete(100L, horses, requirements, List.of(firstDoc, secondDoc, bookingDoc), TODAY)).isTrue();
    }

    @Test
    void matchesHorseOwnerRequirementTypeStatusAndExpiration() {
        var requirement = requirement(1L, 10L, DocumentSubject.HORSE);
        BookingHorse row = horse(11L);
        Horse horse = new Horse();
        horse.setId(7L);
        row.setHorse(horse);
        Document file = document(requirement);
        file.setHorse(horse);
        assertThat(DocumentCompletenessPolicy.satisfies(file, requirement, 100L, row, TODAY)).isTrue();
        file.setExpiryDate(TODAY.minusDays(1));
        assertThat(DocumentCompletenessPolicy.satisfies(file, requirement, 100L, row, TODAY)).isFalse();
        file.setExpiryDate(TODAY);
        file.setStatus(DocumentStatus.REJECTED);
        assertThat(DocumentCompletenessPolicy.satisfies(file, requirement, 100L, row, TODAY)).isFalse();
        file.setStatus(DocumentStatus.APPROVED);
        file.getDocumentType().setId(99L);
        assertThat(DocumentCompletenessPolicy.satisfies(file, requirement, 100L, row, TODAY)).isFalse();
        file.getDocumentType().setId(10L);
        file.getDocumentRequirement().setId(99L);
        assertThat(DocumentCompletenessPolicy.satisfies(file, requirement, 100L, row, TODAY)).isFalse();
    }

    private static BookingHorse horse(Long id) {
        BookingHorse horse = new BookingHorse();
        horse.setId(id);
        return horse;
    }

    private static DocumentRequirementResponseDto requirement(Long id, Long type, DocumentSubject owner) {
        return new DocumentRequirementResponseDto(id, 1L, type, "CODE", "Name", owner, null, DocumentSubmitter.CUSTOMER, true);
    }

    private static Document document(DocumentRequirementResponseDto requirement) {
        DocumentRequirement reference = new DocumentRequirement();
        reference.setId(requirement.id());
        DocumentType type = new DocumentType();
        type.setId(requirement.documentTypeId());
        type.setOwnerType(requirement.ownerType());
        Document document = new Document();
        document.setDocumentRequirement(reference);
        document.setDocumentType(type);
        document.setStatus(DocumentStatus.APPROVED);
        document.setExpiryDate(TODAY);
        return document;
    }

    @Test
    void checksPapersAgainstThePlannedDayOrTodayWhenLate() {
        var booking = new Booking();
        booking.setRequestedPickupDate(LocalDate.of(2026, 12, 8));
        assertThat(DocumentCompletenessPolicy.requiredOn(booking, LocalDate.of(2026, 12, 1))).isEqualTo(LocalDate.of(2026, 12, 8));
        assertThat(DocumentCompletenessPolicy.requiredOn(booking, LocalDate.of(2026, 12, 9))).isEqualTo(LocalDate.of(2026, 12, 9));
        var departure = new Departure();
        departure.setDepartureDate(LocalDate.of(2026, 12, 10));
        booking.setDeparture(departure);
        assertThat(DocumentCompletenessPolicy.requiredOn(booking, LocalDate.of(2026, 12, 9))).isEqualTo(LocalDate.of(2026, 12, 10));
    }
}
