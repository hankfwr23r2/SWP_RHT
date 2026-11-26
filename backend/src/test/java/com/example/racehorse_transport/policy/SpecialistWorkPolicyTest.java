package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.dto.booking.BookingDocumentChecklistResponseDto;
import com.example.racehorse_transport.dto.booking.BookingDocumentChecklistResponseDto.Item;
import com.example.racehorse_transport.dto.booking.BookingDocumentResponseDto;
import com.example.racehorse_transport.dto.regulation.DocumentRequirementResponseDto;
import com.example.racehorse_transport.dto.specialist.SpecialistWorkResponseDto.Legal;
import com.example.racehorse_transport.dto.specialist.SpecialistWorkResponseDto.Verification;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.entity.enums.DocumentSubmitter;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpecialistWorkPolicyTest {
    @Test
    void specialistReviewsTheUploadedProfilePaperEvenAfterQuotingBegins() {
        var requirement = requirement(DocumentSubmitter.CUSTOMER, DocumentSubject.HORSE);
        var item = Item.from(requirement, 22L, false, false, false);
        var doc = new BookingDocumentResponseDto(10L, null, "PASSPORT", 2L, 22L, null, null,
                DocumentStatus.UPLOADED, null, null, null);
        var checklist = new BookingDocumentChecklistResponseDto(false, List.of(item), List.of(doc));
        assertThat(SpecialistWorkPolicy.verification(BookingStatus.QUOTING, checklist)).isEqualTo(Verification.TO_REVIEW);
        assertThat(SpecialistWorkPolicy.verification(BookingStatus.NEEDS_RESUBMISSION, checklist))
                .isEqualTo(Verification.WAITING_CUSTOMER);
    }

    @Test
    void legalWorkMovesThroughPreparationSubmissionAndApproval() {
        var requirement = requirement(DocumentSubmitter.COMPANY, DocumentSubject.BOOKING);
        var item = Item.from(requirement, null, false, false, true);
        var doc = new BookingDocumentResponseDto(10L, requirement.id(), "PASSPORT", null, null, null, null,
                DocumentStatus.UPLOADED, null, null, null);
        assertThat(SpecialistWorkPolicy.legal(new BookingDocumentChecklistResponseDto(false, List.of(item), List.of())))
                .isEqualTo(Legal.TO_PREPARE);
        assertThat(SpecialistWorkPolicy.legal(new BookingDocumentChecklistResponseDto(false, List.of(item), List.of(doc))))
                .isEqualTo(Legal.IN_PROGRESS);
        assertThat(SpecialistWorkPolicy.legal(new BookingDocumentChecklistResponseDto(false,
                List.of(Item.from(requirement, null, true, false, true)), List.of(doc)))).isEqualTo(Legal.COMPLETE);
    }

    @Test
    void aCustomerPaperSpecificToTheBorderAlsoAppearsInLegalWork() {
        var requirement = new DocumentRequirementResponseDto(1L, 2L, 3L, "ATA_CARNET", "Sổ ATA",
                DocumentSubject.BOOKING, 4L, DocumentSubmitter.CUSTOMER, false);
        var item = Item.from(requirement, null, false, false, false);
        assertThat(SpecialistWorkPolicy.legal(new BookingDocumentChecklistResponseDto(false, List.of(item), List.of())))
                .isEqualTo(Legal.COMPLETE);
    }

    private DocumentRequirementResponseDto requirement(DocumentSubmitter side, DocumentSubject subject) {
        return new DocumentRequirementResponseDto(1L, 2L, 3L, "PASSPORT", "Hộ chiếu", subject, null, side, true);
    }
}
