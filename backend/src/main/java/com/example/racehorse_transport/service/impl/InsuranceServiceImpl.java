package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.InsuranceDeclarationRequestDto;
import com.example.racehorse_transport.dto.booking.InsurancePremiumRefundRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.HorseInsurance;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.PartyRole;
import com.example.racehorse_transport.entity.PartyRoleId;
import com.example.racehorse_transport.entity.TermsAcceptance;
import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import com.example.racehorse_transport.entity.enums.TermsKind;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.policy.InsuranceDeclarationPolicy;
import com.example.racehorse_transport.policy.TermsDocumentPolicy;
import com.example.racehorse_transport.repository.HorseInsuranceRepository;
import com.example.racehorse_transport.repository.PartyRoleRepository;
import com.example.racehorse_transport.repository.TermsAcceptanceRepository;
import com.example.racehorse_transport.service.InsuranceService;
import com.example.racehorse_transport.dto.booking.InsuranceEvidenceRequestDto;
import com.example.racehorse_transport.dto.booking.PersonalInsuranceRevisionRequestDto;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.InsuranceCoverageOption;
import com.example.racehorse_transport.entity.enums.PickupMissLiability;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingReviewPolicy;
import com.example.racehorse_transport.policy.InsuranceReviewPolicy;
import com.example.racehorse_transport.policy.PickupMissPricingPolicy;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.service.BookingAssignmentService;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.BookingHorseService;
import com.example.racehorse_transport.service.NotificationService;
import static com.example.racehorse_transport.policy.BookingSubmissionPolicy.businessDate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InsuranceServiceImpl implements InsuranceService {

    private final PartyRoleRepository roles;
    private final TermsAcceptanceRepository terms;
    private final HorseInsuranceRepository insurances;
    private final BookingRepository bookings;
    private final BookingAssignmentService assignments;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;
    private final BookingHorseService bookingHorses;

    @Override
    @Transactional
    public void recordPremiumRefund(Long bookingId, Long insuranceId, InsurancePremiumRefundRequestDto request, String username) {
        var actor = assignments.requireAssigned(lockBooking(bookingId), username);
        var insurance = insurances.findById(insuranceId)
                .filter(row -> bookingId.equals(row.getBookingHorse().getBooking().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bảo hiểm của đơn."));
        var horse = insurance.getBookingHorse();
        if (horse.getPickupMissLiability() != PickupMissLiability.CUSTOMER) {
            throw BookingRuleException.conflict("Chỉ đối soát phí hoàn cho trường hợp đón hụt do khách.");
        }
        if (!PickupMissPricingPolicy.newPremiumRefund(insurance, request.refundedPremiumAmount())) return;
        insurance.setRefundedPremiumAmount(request.refundedPremiumAmount());
        audit.record(actor.getUserId(), "RECORD_INSURANCE_PREMIUM_REFUND", "horse_insurance", insuranceId, null,
                Map.of("refundedPremiumAmount", request.refundedPremiumAmount(), "receiptReference", request.receiptReference().strip()));
        if (horse.getStatus() == BookingHorseStatus.EXCLUDED && horse.getBooking().getStatus() != BookingStatus.CANCELLED) {
            bookingHorses.reviseExcludedInsurance(horse);
        }
    }

    @Override
    @Transactional
    public void recordEvidence(Long bookingId, Long insuranceId, InsuranceEvidenceRequestDto request, String username) {
        Booking booking = lockBooking(bookingId);
        assignments.requireAssigned(booking, username);
        HorseInsurance insurance = editableInsurance(booking, insuranceId);
        InsuranceReviewPolicy.requireStatus(insurance, HorseInsuranceStatus.DRAFT);
        InsuranceReviewPolicy.validateEvidence(insurance.getCoverageOption(), request.coverageAmount(),
                request.premiumAmount(), request.currencyCode(), request.quoteExpiresAt(), clock.instant());
        request.applyTo(insurance, insurer(request.insurerPartyId(), businessDate(clock.instant())));
        insurance.setCoverageAmount(request.coverageAmount());
        insurance.setPremiumAmount(request.premiumAmount());
    }

    @Override
    @Transactional
    public void confirm(Long bookingId, Long insuranceId, String username) {
        Booking booking = lockBooking(bookingId);
        Staff actor = assignments.requireAssigned(booking, username);
        HorseInsurance insurance = editableInsurance(booking, insuranceId);
        InsuranceReviewPolicy.validateConfirmation(insurance, booking.getRequestedPickupDate(), departureDate(booking),
                clock.instant());
        if (insurance.getCoverageOption() != InsuranceCoverageOption.NONE) {
            insurer(insurance.getInsurerParty().getId(), businessDate(clock.instant()));
        }
        insurance.setConfirmedBy(actor);
        insurance.setConfirmedAt(clock.instant());
        insurance.setStatus(HorseInsuranceStatus.CONFIRMED);
    }

    @Override
    @Transactional(readOnly = true)
    public void validateForQuoting(Booking booking, List<BookingHorse> activeHorses) {
        if (activeHorses.isEmpty()) {
            return;
        }
        List<Long> ids = activeHorses.stream().map(BookingHorse::getId).toList();
        Map<Long, HorseInsurance> policies = insurances.findForDetail(ids).stream()
                .collect(Collectors.toMap(row -> row.getBookingHorse().getId(), Function.identity()));
        Instant now = clock.instant();
        LocalDate departure = departureDate(booking);
        for (BookingHorse horse : activeHorses) {
            InsuranceReviewPolicy.validateForQuoting(policies.get(horse.getId()), horse.getHorse().getName(),
                    booking.getRequestedPickupDate(), departure, now);
        }
    }

    @Override
    @Transactional
    public void reopen(Long bookingId, Long insuranceId, String username, String reason) {
        Booking booking = lockBooking(bookingId);
        Staff actor = assignments.requireAssigned(booking, username);
        HorseInsurance insurance = editableInsurance(booking, insuranceId);
        InsuranceReviewPolicy.requireStatus(insurance, HorseInsuranceStatus.CONFIRMED);
        String note = BookingReviewPolicy.requireReason(reason);
        insurance.setStatus(HorseInsuranceStatus.DRAFT);
        insurance.setConfirmedBy(null);
        insurance.setConfirmedAt(null);
        audit.record(actor.getUserId(), "REOPEN_INSURANCE", "horse_insurance", insuranceId,
                Map.of("status", HorseInsuranceStatus.CONFIRMED),
                Map.of("status", HorseInsuranceStatus.DRAFT, "reason", note));
    }

    @Override
    @Transactional
    public void requestRevision(Long bookingId, Long insuranceId, String username, String reason) {
        Booking booking = lockBooking(bookingId);
        Staff actor = assignments.requireAssigned(booking, username);
        requireReview(booking);
        HorseInsurance insurance = activeInsurance(bookingId, insuranceId);
        InsuranceReviewPolicy.requirePersonal(insurance);
        InsuranceReviewPolicy.requireStatus(insurance, HorseInsuranceStatus.DRAFT);
        String note = BookingReviewPolicy.requireReason(reason);
        insurance.setRevisionReason(note);
        insurance.setRevisionRequestedBy(actor);
        insurance.setRevisionRequestedAt(clock.instant());
        insurance.setStatus(HorseInsuranceStatus.REVISION_REQUESTED);
        audit.record(actor.getUserId(), "REQUEST_INSURANCE_REVISION", "horse_insurance", insuranceId,
                Map.of("status", HorseInsuranceStatus.DRAFT),
                Map.of("status", HorseInsuranceStatus.REVISION_REQUESTED, "revisionReason", note));
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), bookingId,
                "Hợp đồng bảo hiểm cần bổ sung", note, null);
    }

    @Override
    @Transactional
    public void revise(Long bookingId, Long insuranceId, String username, PersonalInsuranceRevisionRequestDto request) {
        Booking booking = lockBooking(bookingId);
        if (!booking.getCustomer().getUser().getUsername().equals(username)) {
            throw missingBooking();
        }
        requireReview(booking);
        HorseInsurance insurance = activeInsurance(bookingId, insuranceId);
        InsuranceReviewPolicy.requirePersonal(insurance);
        InsuranceReviewPolicy.requireStatus(insurance, HorseInsuranceStatus.REVISION_REQUESTED);
        InsuranceReviewPolicy.validatePeriod(request.validFrom(), request.validTo(),
                booking.getRequestedPickupDate(), departureDate(booking));
        request.applyTo(insurance, insurer(request.insurerPartyId(), businessDate(clock.instant())));
        insurance.setCoverageAmount(null);
        insurance.setCurrencyCode(null);
        insurance.setStatus(HorseInsuranceStatus.DRAFT);
        var recipients = assignments.recipients(booking);
        if (!recipients.isEmpty()) {
            notifications.notify(recipients, bookingId, "Hợp đồng bảo hiểm đã bổ sung",
                    "Kiểm tra lại chứng từ bảo hiểm trong chi tiết đơn.", assignments.reviewLink(booking));
        }
    }

    private static LocalDate departureDate(Booking booking) {
        return booking.getDeparture() == null ? null : booking.getDeparture().getDepartureDate();
    }

    private static void requireReview(Booking booking) {
        BookingReviewPolicy.requireStatus(booking.getStatus(), BookingStatus.UNDER_REVIEW, BookingStatus.NEEDS_RESUBMISSION);
    }

    private HorseInsurance editableInsurance(Booking booking, Long insuranceId) {
        boolean quoting = booking.getStatus() == BookingStatus.QUOTING;
        if (!quoting) {
            requireReview(booking);
        }
        HorseInsurance insurance = activeInsurance(booking.getId(), insuranceId);
        if (quoting && insurance.getCoverageOption() != InsuranceCoverageOption.ARRANGED) {
            throw BookingRuleException.conflict("Chỉ bảo hiểm mua hộ được cập nhật khi đơn đang báo giá.");
        }
        return insurance;
    }

    private Booking lockBooking(Long id) {
        return bookings.findForUpdate(id).orElseThrow(InsuranceServiceImpl::missingBooking);
    }

    private static ResourceNotFoundException missingBooking() {
        return new ResourceNotFoundException("Không tìm thấy đơn.");
    }

    private HorseInsurance activeInsurance(Long bookingId, Long insuranceId) {
        return insurances.findById(insuranceId)
                .filter(row -> row.getBookingHorse().getBooking().getId().equals(bookingId)
                        && row.getBookingHorse().getStatus() == BookingHorseStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bảo hiểm của đơn."));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void declare(BookingHorse horse, InsuranceDeclarationRequestDto request, LocalDate submittedOn, String ipAddress) {
        InsuranceDeclarationPolicy.validate(request);
        HorseInsurance insurance = switch (request.coverageOption()) {
            case PERSONAL -> request.toEntity(horse, insurer(request.insurerPartyId(), submittedOn), null);
            case ARRANGED -> request.toEntity(horse, null, null);
            case NONE -> request.toEntity(horse, null, acceptWaiver(horse.getBooking(), ipAddress));
        };
        insurance.setStatus(HorseInsuranceStatus.DRAFT);
        insurances.save(insurance);
    }

    private Party insurer(Long id, LocalDate submittedOn) {
        if (id == null) {
            return null;
        }
        return roles.findById(new PartyRoleId(id, PartyRoleType.INSURER))
                .filter(role -> InsuranceDeclarationPolicy.isRoleEffective(role, submittedOn))
                .map(PartyRole::getParty)
                .orElseThrow(() -> new BookingRuleException("Đối tác chưa có vai trò bảo hiểm còn hiệu lực."));
    }

    private TermsAcceptance acceptWaiver(Booking booking, String ipAddress) {
        TermsAcceptance acceptance = new TermsAcceptance();
        acceptance.setBooking(booking);
        acceptance.setUser(booking.getCreatedBy());
        acceptance.setTermsType(TermsKind.LIABILITY_WAIVER);
        acceptance.setTermsVersion(TermsDocumentPolicy.LIMITED_LIABILITY_VERSION);
        acceptance.setIpAddress(ipAddress);
        return terms.save(acceptance);
    }
}
