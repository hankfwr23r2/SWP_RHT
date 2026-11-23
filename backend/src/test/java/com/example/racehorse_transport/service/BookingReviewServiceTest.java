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
    private static final MockMultipartFile FILE = new MockMultipartFile("file", new byte[] {1});

    private final Booking booking = new Booking();
    private final BookingHorse horse = new BookingHorse();
    private final HorseInsurance cover = new HorseInsurance();
    private final Staff specialist = new Staff();
    private final AppUser customerUser = new AppUser();
    private final List<Document> files = new ArrayList<>();
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final BookingHorseRepository horses = mock(BookingHorseRepository.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final DocumentRequirementRepository requirements = mock(DocumentRequirementRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final DocumentService attachments = mock(DocumentService.class);
    private final HorseInsuranceRepository insurances = mock(HorseInsuranceRepository.class);
    private final PartyRoleRepository roles = mock(PartyRoleRepository.class);
    private final BookingRoutingService routing = mock(BookingRoutingService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AuditService audit = mock(AuditService.class);
    private final DocumentCompletenessServiceImpl completeness = spy(new DocumentCompletenessServiceImpl(
            bookings, mock(BookingAddressRepository.class), horses, documents, mock(RegulationService.class), CLOCK));
    private final DocumentReviewService docs = new DocumentReviewServiceImpl(
            bookings, horses, documents, requirements, staff, attachments, completeness, notifications, CLOCK);
    private final InsuranceService insurance = new InsuranceServiceImpl(
            roles, mock(TermsAcceptanceRepository.class), insurances, bookings, staff, notifications, audit, CLOCK);
    private BookingReviewService review;

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
        when(staff.findByUser_Username("specialist")).thenReturn(Optional.of(specialist));
        when(documents.findForBookingReview(BOOKING_ID)).thenReturn(files);

        AppUser actor = new AppUser();
        actor.setId(STAFF_ID);
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findByUsername("specialist")).thenReturn(Optional.of(actor));
        BookingStatusService statuses = mock(BookingStatusService.class);
        doAnswer(call -> {
            booking.setStatus(call.getArgument(1));
            return null;
        }).when(statuses).transition(BOOKING_ID, QUOTING, null, STAFF_ID);
        review = new BookingReviewServiceImpl(bookings, users, horses, insurance, statuses, routing,
                mock(DepartureService.class), completeness, notifications, mock(BookingStatusHistoryRepository.class));
        resolve(false);
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
            verify(attachments).attach(FILE, requirement.documentTypeCode(), DocumentSubject.BOOKING, BOOKING_ID, STAFF_ID);
        }
        assertThat(completeness.isComplete(BOOKING_ID)).isTrue();
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
        review.changeDeparture(BOOKING_ID, 11L);
        assertConflict(() -> review.startQuoting(BOOKING_ID, "specialist"), "Sao Mai");

        insurance.reopen(BOOKING_ID, INSURANCE_ID, "specialist", "  Đổi chuyến  ");
        assertThat(cover.getStatus()).isEqualTo(HorseInsuranceStatus.DRAFT);
        assertThat(cover.getConfirmedBy()).isNull();
        assertThat(cover.getConfirmedAt()).isNull();
        verify(audit).record(STAFF_ID, "REOPEN_INSURANCE", "horse_insurance", INSURANCE_ID,
                Map.of("status", HorseInsuranceStatus.CONFIRMED),
                Map.of("status", HorseInsuranceStatus.DRAFT, "reason", "Đổi chuyến"));

        Party insurer = new Party();
        insurer.setId(8L);
        PartyRole role = new PartyRole();
        role.setParty(insurer);
        role.setValidFrom(PICKUP.minusMonths(2));
        when(roles.findById(new PartyRoleId(8L, PartyRoleType.INSURER))).thenReturn(Optional.of(role));
        insurance.requestRevision(BOOKING_ID, INSURANCE_ID, "specialist", "Cập nhật hạn hợp đồng");
        insurance.revise(BOOKING_ID, INSURANCE_ID, "customer",
                new PersonalInsuranceRevisionRequestDto(8L, "new-policy", PICKUP, replacement.getDepartureDate()));
        insurance.recordEvidence(BOOKING_ID, INSURANCE_ID,
                new InsuranceEvidenceRequestDto(8L, BigDecimal.TEN, null, "VND", null));
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
        booking.setStatus(QUOTING);
        assertConflict(() -> insurance.reopen(BOOKING_ID, INSURANCE_ID, "specialist", "Đổi chuyến"), "trạng thái");
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
        when(attachments.attach(FILE, requirement.documentTypeCode(), DocumentSubject.BOOKING, BOOKING_ID, uploader))
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
