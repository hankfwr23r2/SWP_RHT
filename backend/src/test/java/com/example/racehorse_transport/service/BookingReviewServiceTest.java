package com.example.racehorse_transport.service;

import static com.example.racehorse_transport.entity.enums.BookingStatus.*;
import static com.example.racehorse_transport.entity.enums.DocumentSubmitter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.racehorse_transport.dto.booking.*;
import com.example.racehorse_transport.dto.regulation.DocumentRequirementResponseDto;
import com.example.racehorse_transport.dto.regulation.RegulationPreviewResponseDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.impl.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class BookingReviewServiceTest {
    private static final long BOOKING_ID = 1L;
    private static final long STAFF_ID = 2L;
    private static final long CUSTOMER_ID = 3L;
    private static final long HORSE_ID = 4L;
    private static final long INSURANCE_ID = 5L;
    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final LocalDate PICKUP = LocalDate.of(2026, 12, 1);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final MockMultipartFile FILE = new MockMultipartFile("file", new byte[] {'%', 'P', 'D', 'F', '-', '1', '.', '7'});

    private final Booking booking = new Booking();
    private final DepartureService departures = mock(DepartureService.class);
    private final BookingHorse horse = new BookingHorse();
    private final HorseInsurance cover = new HorseInsurance();
    private final Staff specialist = new Staff();
    private final AppUser customerUser = new AppUser();
    private final List<Document> files = new ArrayList<>();
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final BookingHorseRepository horses = mock(BookingHorseRepository.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final DocumentRequirementRepository requirements = mock(DocumentRequirementRepository.class);
    private final BookingAssignmentService assignments = mock(BookingAssignmentService.class);
    private final DocumentService attachments = mock(DocumentService.class);
    private final HorseInsuranceRepository insurances = mock(HorseInsuranceRepository.class);
    private final BookingRoutingService routing = mock(BookingRoutingService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AuditService audit = mock(AuditService.class);
    private final BookingHorseService bookingHorses = mock(BookingHorseService.class);
    private final DocumentCompletenessServiceImpl completeness = spy(new DocumentCompletenessServiceImpl(
            bookings, mock(BookingAddressRepository.class), horses, documents, mock(RegulationService.class), CLOCK));
    private final DocumentReviewService docs = new DocumentReviewServiceImpl(
            bookings, horses, documents, requirements, assignments, attachments, completeness, notifications, audit, CLOCK);
    private final InsuranceService insurance = new InsuranceServiceImpl(
            mock(TermsAcceptanceRepository.class), insurances, bookings, assignments, notifications, audit, CLOCK,
            bookingHorses, attachments);
    private final BookingStatusHistoryRepository history = mock(BookingStatusHistoryRepository.class);
    private final BookingStatusService statuses = mock(BookingStatusService.class);
    private BookingReviewService review;

    @Test
    void recordedPremiumRefundUpdatesExcludedHorseQuotationOnlyOnce() {
        booking.setStatus(IN_OPERATION);
        horse.setPickupMissLiability(PickupMissLiability.CUSTOMER);
        horse.setStatus(BookingHorseStatus.EXCLUDED);
        cover.setCoverageOption(InsuranceCoverageOption.ARRANGED);
        cover.setPremiumAmount(new BigDecimal("1500000"));
        var request = new InsurancePremiumRefundRequestDto(new BigDecimal("500000"), "INS-057");

        insurance.recordPremiumRefund(BOOKING_ID, INSURANCE_ID, request, "specialist");
        insurance.recordPremiumRefund(BOOKING_ID, INSURANCE_ID, request, "specialist");

        assertThat(cover.getRefundedPremiumAmount()).isEqualByComparingTo("500000");
        verify(bookings, times(2)).findForUpdate(BOOKING_ID);
        verify(bookingHorses).reviseExcludedInsurance(horse);
        verify(audit).record(STAFF_ID, "RECORD_INSURANCE_PREMIUM_REFUND", "horse_insurance", INSURANCE_ID,
                null, Map.of("refundedPremiumAmount", new BigDecimal("500000"), "receiptReference", "INS-057"));
        assertThatThrownBy(() -> insurance.recordPremiumRefund(BOOKING_ID, INSURANCE_ID,
                new InsurancePremiumRefundRequestDto(new BigDecimal("1500001"), "INS-058"), "specialist"))
                .isInstanceOf(BookingRuleException.class);
        verifyNoMoreInteractions(bookingHorses);
    }

    @BeforeEach
    void setup() {
        booking.setId(BOOKING_ID);
        booking.setBookingType(BookingType.EXPORT);
        booking.setStatus(UNDER_REVIEW);
        booking.setRequestedPickupDate(PICKUP);
        booking.setDeparture(departure(10L, PICKUP.plusDays(1)));
        customerUser.setId(CUSTOMER_ID);
        customerUser.setUsername("customer");
        Customer customer = new Customer();
        customer.setUser(customerUser);
        booking.setCustomer(customer);

        horse.setId(HORSE_ID);
        horse.setBooking(booking);
        horse.setStatus(BookingHorseStatus.ACTIVE);
        StallType wide = new StallType();
        wide.setCode("WIDE");
        horse.setStallType(wide);
        Horse animal = new Horse();
        animal.setName("Sao Mai");
        horse.setHorse(animal);
        specialist.setUserId(STAFF_ID);

        cover.setId(INSURANCE_ID);
        cover.setBookingHorse(horse);
        cover.setStatus(HorseInsuranceStatus.CONFIRMED);
        cover.setCoverageOption(InsuranceCoverageOption.NONE);
        cover.setConfirmedBy(specialist);
        cover.setConfirmedAt(NOW);
        cover.setPolicyNumber("policy-secret");

        when(bookings.findById(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(bookings.findForUpdate(BOOKING_ID)).thenReturn(Optional.of(booking));
        when(horses.findByBooking_Id(BOOKING_ID)).thenReturn(List.of(horse));
        when(horses.findForDetail(BOOKING_ID)).thenReturn(List.of(horse));
        when(insurances.findForDetail(List.of(HORSE_ID))).thenReturn(List.of(cover));
        when(insurances.findById(INSURANCE_ID)).thenReturn(Optional.of(cover));
        when(assignments.requireAssigned(booking, "specialist")).thenReturn(specialist);
        when(documents.findForBookingReview(BOOKING_ID)).thenReturn(files);

        AppUser actor = new AppUser();
        actor.setId(STAFF_ID);
        when(assignments.actor(anyString())).thenReturn(actor);
        doAnswer(call -> {
            booking.setStatus(call.getArgument(1));
            return null;
        }).when(statuses).transition(BOOKING_ID, QUOTING, null, STAFF_ID);
        review = new BookingReviewServiceImpl(bookings, assignments, horses, insurance, statuses, routing,
                departures, completeness, notifications, history);
        resolve(false);
    }

    @Test
    void reopeningExpiredQuoteReservesHistoricalDepartureAndUpdatesOnce() {
        booking.setStatus(QUOTE_EXPIRED);
        var previous = booking.getDeparture();
        booking.setDeparture(null);
        var expired = new BookingStatusHistory();
        expired.setPreviousDeparture(previous);
        when(history.findFirstByBooking_IdAndToStatusOrderByChangedAtDescIdDesc(BOOKING_ID, QUOTE_EXPIRED))
                .thenReturn(Optional.of(expired));
        when(routing.lockReplacement(booking, previous.getId())).thenReturn(previous);
        doAnswer(call -> {
            assertThat(booking.getDeparture()).isSameAs(previous);
            return true;
        }).when(completeness).isCompleteForQuoting(BOOKING_ID);
        doAnswer(call -> {
            booking.setStatus(QUOTING);
            return null;
        }).when(statuses).transition(eq(BOOKING_ID), eq(QUOTING), anyString(), eq(STAFF_ID));

        assertThat(review.reopenExpiredQuote(BOOKING_ID, "specialist").status()).isEqualTo(QUOTING);
        assertThat(booking.getDeparture()).isSameAs(previous);
        assertThatThrownBy(() -> review.reopenExpiredQuote(BOOKING_ID, "specialist")).isInstanceOf(BookingRuleException.class);
        verify(departures).reserve(previous.getId(), 2);
        verify(notifications).notify(eq(List.of(CUSTOMER_ID)), eq(BOOKING_ID), anyString(), anyString(), isNull());
    }

    @Test
    void reopeningExpiredQuoteDoesNotTransitionWhenDepartureHasNoCapacity() {
        booking.setStatus(QUOTE_EXPIRED);
        var previous = booking.getDeparture();
        when(routing.lockReplacement(booking, previous.getId())).thenReturn(previous);
        doThrow(BookingRuleException.conflict("Departure full")).when(departures).reserve(previous.getId(), 2);

        assertThatThrownBy(() -> review.reopenExpiredQuote(BOOKING_ID, "specialist"))
                .isInstanceOfSatisfying(BookingRuleException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getReason()).isEqualTo("Departure full");
                });
        assertThat(booking.getStatus()).isEqualTo(QUOTE_EXPIRED);
        verifyNoInteractions(statuses, notifications);
    }

    @Test
    void exportCanQuoteBeforePartnerDocumentsButCannotPickUpUntilAllAreApproved() {
        var passport = requirement(20L, CUSTOMER, true);
        var certificate = requirement(21L, COMPANY, true);
        var permit = requirement(22L, CONSIGNEE, true);
        resolve(false, passport, certificate, permit);
        files.add(document(passport, DocumentStatus.APPROVED));

        assertThat(review.startQuoting(BOOKING_ID, "specialist").status()).isEqualTo(QUOTING);
        assertThat(completeness.isComplete(BOOKING_ID)).isFalse();
        var items = docs.checklist(BOOKING_ID, null).items();
        assertThat(items).extracting(BookingDocumentChecklistResponseDto.Item::submitterType)
                .containsExactly(CUSTOMER, COMPANY, CONSIGNEE);
        assertThat(items).extracting(BookingDocumentChecklistResponseDto.Item::requiredForQuoting)
                .containsExactly(true, false, false);
        assertThat(items).allMatch(BookingDocumentChecklistResponseDto.Item::requiredBeforePickup);

        for (var requirement : List.of(certificate, permit)) {
            prepareUpload(requirement, STAFF_ID);
            assertThat(docs.attachForPartner(BOOKING_ID, "specialist", partnerUpload(requirement)).status())
                    .isEqualTo(DocumentStatus.UPLOADED);
            assertThat(completeness.isComplete(BOOKING_ID)).isFalse();
            assertThat(docs.approve(BOOKING_ID, requirement.id(), "specialist", null).status())
                    .isEqualTo(DocumentStatus.APPROVED);
            verify(attachments).attachForReview(eq(FILE), eq(requirement.documentTypeCode()), eq(DocumentSubject.BOOKING), eq(BOOKING_ID),
                    eq(STAFF_ID), eq(requirement.id()), any());
        }
        assertThat(completeness.isComplete(BOOKING_ID)).isTrue();
    }

    @Test
    void rejectsDocumentsForTheCorrectSide() {
        var passport = requirement(20L, CUSTOMER, true);
        var certificate = requirement(21L, COMPANY, true);
        resolve(false, passport, certificate);
        when(assignments.recipients(booking)).thenReturn(List.of(STAFF_ID));
        when(assignments.reviewLink(booking)).thenReturn("/specialist/review/" + BOOKING_ID);
        prepareUpload(passport, CUSTOMER_ID);
        prepareUpload(certificate, STAFF_ID);
        docs.attach(BOOKING_ID, "customer", customerUpload(passport));
        docs.attachForPartner(BOOKING_ID, "specialist", partnerUpload(certificate));
        docs.reject(BOOKING_ID, passport.id(), "specialist", "Sai hộ chiếu");
        docs.reject(BOOKING_ID, certificate.id(), "specialist", "Sai giấy chứng nhận");
        verify(notifications).notify(List.of(CUSTOMER_ID), BOOKING_ID, "Giấy tờ cần bổ sung",
                "Sai hộ chiếu", "/orders/" + BOOKING_ID);
        verify(notifications).notify(List.of(STAFF_ID), BOOKING_ID, "Giấy tờ cần bổ sung",
                "Sai giấy chứng nhận", "/specialist/review/" + BOOKING_ID);
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void checklistAndCompletenessUseTripDateOrDirectPickupDate() {
        var passport = requirement(20L, CUSTOMER, true);
        var certificate = requirement(21L, COMPANY, true);
        resolve(false, passport, certificate);
        var approved = document(passport, DocumentStatus.APPROVED);
        approved.setExpiryDate(PICKUP);
        files.add(approved);

        assertThat(completeness.isCompleteForQuoting(BOOKING_ID)).isFalse();
        assertThat(completeness.missingSubmitters(BOOKING_ID)).containsExactlyInAnyOrder(CUSTOMER, COMPANY);
        assertThat(docs.checklist(BOOKING_ID, null).items().getFirst().satisfied()).isFalse();
        booking.setBookingType(BookingType.INTERPROVINCE);
        booking.setDeparture(null);
        assertThat(completeness.isCompleteForQuoting(BOOKING_ID)).isTrue();
        assertThat(completeness.missingSubmitters(BOOKING_ID)).containsExactly(COMPANY);
        assertThat(docs.checklist(BOOKING_ID, null).items().getFirst().satisfied()).isTrue();
    }

    @Test
    void bothMilestonesWaitForGateRequirements() {
        resolve(true);
        assertThat(completeness.isCompleteForQuoting(BOOKING_ID)).isFalse();
        assertThat(completeness.isComplete(BOOKING_ID)).isFalse();
    }

    @Test
    void partnerAndCustomerCannotUploadForEachOther() {
        var passport = requirement(20L, CUSTOMER, true);
        var certificate = requirement(21L, COMPANY, true);
        resolve(false, passport, certificate);
        assertConflict(() -> docs.attachForPartner(BOOKING_ID, "specialist", partnerUpload(passport)), "khách hàng");
        assertConflict(() -> docs.attach(BOOKING_ID, "customer", customerUpload(certificate)), "khách hàng");
        verifyNoInteractions(attachments);
    }

    @Test
    void confirmedCustomerCanReplaceExpiredMandatoryDocument() {
        booking.setStatus(CONFIRMED);
        var passport = requirement(20L, CUSTOMER, true);
        var optional = requirement(21L, CUSTOMER, false);
        resolve(false, passport, optional);
        Document expired = document(passport, DocumentStatus.APPROVED);
        expired.setId(30L);
        expired.setExpiryDate(PICKUP.minusMonths(1));
        files.add(expired);
        prepareUpload(passport, CUSTOMER_ID);

        assertConflict(() -> docs.attach(BOOKING_ID, "customer", customerUpload(optional)), "trạng thái");
        assertThat(docs.attach(BOOKING_ID, "customer", customerUpload(passport)).status())
                .isEqualTo(DocumentStatus.UPLOADED);
        assertConflict(() -> docs.attach(BOOKING_ID, "customer", customerUpload(passport)), "chờ duyệt");
        docs.approve(BOOKING_ID, passport.id(), "specialist", null);
        assertConflict(() -> docs.attach(BOOKING_ID, "customer", customerUpload(passport)), "còn hiệu lực");
    }

    @Test
    void changedDepartureBlocksPersonalInsuranceUntilReopenedRevisedAndConfirmed() {
        cover.setCoverageOption(InsuranceCoverageOption.PERSONAL);
        cover.setValidFrom(PICKUP);
        cover.setValidTo(PICKUP.plusDays(1));
        Departure replacement = departure(11L, PICKUP.plusDays(5));
        when(routing.lockReplacement(booking, 11L)).thenReturn(replacement);
        BookingHorse removed = new BookingHorse();
        removed.setStatus(BookingHorseStatus.EXCLUDED);
        removed.setStallType(horse.getStallType());
        when(horses.findByBooking_Id(BOOKING_ID)).thenReturn(List.of(horse, removed));
        review.changeDeparture(BOOKING_ID, 11L);
        verify(departures).reserve(11L, 2);
        verify(departures).release(10L, 2);
        assertConflict(() -> review.startQuoting(BOOKING_ID, "specialist"), "Sao Mai");

        insurance.reopen(BOOKING_ID, INSURANCE_ID, "specialist", "  Đổi chuyến  ");
        assertThat(cover.getStatus()).isEqualTo(HorseInsuranceStatus.DRAFT);
        assertThat(cover.getConfirmedBy()).isNull();
        assertThat(cover.getConfirmedAt()).isNull();
        verify(audit).record(STAFF_ID, "REOPEN_INSURANCE", "horse_insurance", INSURANCE_ID,
                Map.of("status", HorseInsuranceStatus.CONFIRMED),
                Map.of("status", HorseInsuranceStatus.DRAFT, "reason", "Đổi chuyến"));

        insurance.requestRevision(BOOKING_ID, INSURANCE_ID, "specialist", "Cập nhật hạn hợp đồng");
        insurance.revise(BOOKING_ID, INSURANCE_ID, "customer",
                new PersonalInsuranceRevisionRequestDto("Hãng A", "new-policy", PICKUP, replacement.getDepartureDate()), FILE);
        insurance.recordEvidence(BOOKING_ID, INSURANCE_ID,
                new InsuranceEvidenceRequestDto("Hãng A", BigDecimal.TEN, null, "VND", null), "specialist");
        when(insurances.hasPolicyFile(INSURANCE_ID, "INSURANCE_POLICY")).thenReturn(true);
        insurance.confirm(BOOKING_ID, INSURANCE_ID, "specialist");
        assertThat(review.startQuoting(BOOKING_ID, "specialist").status()).isEqualTo(QUOTING);
    }

    @Test
    void expiredArrangedQuoteBlocksStartQuotingAndNamesHorse() {
        cover.setCoverageOption(InsuranceCoverageOption.ARRANGED);
        cover.setQuoteExpiresAt(NOW);
        assertConflict(() -> review.startQuoting(BOOKING_ID, "specialist"), "Sao Mai");
        cover.setQuoteExpiresAt(NOW.plusSeconds(1));
        assertThat(review.startQuoting(BOOKING_ID, "specialist").status()).isEqualTo(QUOTING);
    }

    @Test
    void missingInsuranceForAnotherActiveHorseBlocksQuoting() {
        BookingHorse second = new BookingHorse();
        second.setId(6L);
        second.setStatus(BookingHorseStatus.ACTIVE);
        StallType wide = new StallType();
        wide.setCode("WIDE");
        horse.setStallType(wide);
        Horse animal = new Horse();
        animal.setName("Sao Hôm");
        second.setHorse(animal);
        when(horses.findForDetail(BOOKING_ID)).thenReturn(List.of(horse, second));
        when(insurances.findForDetail(List.of(HORSE_ID, 6L))).thenReturn(List.of(cover));
        assertConflict(() -> review.startQuoting(BOOKING_ID, "specialist"), "Sao Hôm");
    }

    @Test
    void reopenRequiresReasonAndReviewStatusAndConfirmedInsurance() {
        assertThatThrownBy(() -> insurance.reopen(BOOKING_ID, INSURANCE_ID, "specialist", " "))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("lý do");
        // Đang báo giá chỉ mở lại được bảo hiểm mua hộ (2.F4); bảo hiểm khách tự có vẫn bị khóa
        booking.setStatus(QUOTING);
        cover.setCoverageOption(InsuranceCoverageOption.PERSONAL);
        assertConflict(() -> insurance.reopen(BOOKING_ID, INSURANCE_ID, "specialist", "Đổi chuyến"), "mua hộ");
        cover.setCoverageOption(InsuranceCoverageOption.ARRANGED);
        assertThat(cover.getStatus()).isEqualTo(HorseInsuranceStatus.CONFIRMED);
        assertThat(cover.getConfirmedAt()).isEqualTo(NOW);
        booking.setStatus(NEEDS_RESUBMISSION);
        cover.setStatus(HorseInsuranceStatus.DRAFT);
        assertConflict(() -> insurance.reopen(BOOKING_ID, INSURANCE_ID, "specialist", "Đổi chuyến"), "trạng thái");
        verifyNoInteractions(audit);
    }

    private void resolve(boolean dependsOnGate, DocumentRequirementResponseDto... rows) {
        doReturn(new RegulationPreviewResponseDto(null, 0, List.of(rows), dependsOnGate, null, null))
                .when(completeness).resolve(booking);
    }

    private void prepareUpload(DocumentRequirementResponseDto requirement, long uploader) {
        Document pending = document(requirement, DocumentStatus.UPLOADED);
        DocumentRequirement reference = pending.getDocumentRequirement();
        pending.setDocumentRequirement(null);
        when(requirements.getReferenceById(requirement.id())).thenReturn(reference);
        when(documents.findById(requirement.id())).thenReturn(Optional.of(pending));
        when(documents.findForReviewUpdate(requirement.id())).thenReturn(Optional.of(pending));
        when(attachments.attachForReview(eq(FILE), eq(requirement.documentTypeCode()), eq(DocumentSubject.BOOKING), eq(BOOKING_ID),
                eq(uploader), eq(requirement.id()), any()))
                .thenAnswer(call -> {
                    files.add(pending);
                    return pending.getId();
                });
    }

    private Document document(DocumentRequirementResponseDto requirement, DocumentStatus status) {
        DocumentType type = new DocumentType();
        type.setId(requirement.documentTypeId());
        type.setCode(requirement.documentTypeCode());
        type.setOwnerType(DocumentSubject.BOOKING);
        DocumentRequirement reference = new DocumentRequirement();
        reference.setId(requirement.id());
        reference.setSubmitterType(requirement.submitterType());
        Document row = new Document();
        row.setId(requirement.id());
        row.setDocumentType(type);
        row.setDocumentRequirement(reference);
        row.setBooking(booking);
        row.setStatus(status);
        return row;
    }

    private static DocumentRequirementResponseDto requirement(long id, DocumentSubmitter submitter, boolean mandatory) {
        return new DocumentRequirementResponseDto(id, 1L, id, "DOC_" + id, "Document",
                DocumentSubject.BOOKING, null, submitter, mandatory);
    }

    private static PartnerDocumentUploadRequestDto partnerUpload(DocumentRequirementResponseDto requirement) {
        return new PartnerDocumentUploadRequestDto(requirement.id(), null, null, FILE);
    }

    private static DocumentUploadRequestDto customerUpload(DocumentRequirementResponseDto requirement) {
        return new DocumentUploadRequestDto(requirement.id(), null, null, FILE);
    }

    private static Departure departure(long id, LocalDate date) {
        Departure departure = new Departure();
        departure.setId(id);
        departure.setDepartureDate(date);
        return departure;
    }

    private static void assertConflict(ThrowingCallable action, String message) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BookingRuleException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getReason()).containsIgnoringCase(message);
        });
    }
}
