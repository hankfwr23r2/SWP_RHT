package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.InsuranceDeclarationRequestDto;
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
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingReviewPolicy;
import com.example.racehorse_transport.policy.InsuranceReviewPolicy;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.AuditService;
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
    private final StaffRepository staff;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    @Override
    @Transactional
    public void recordEvidence(Long bookingId, Long insuranceId, InsuranceEvidenceRequestDto request) {
        Booking booking = lockBooking(bookingId);
        requireReview(booking);
        HorseInsurance insurance = activeInsurance(bookingId, insuranceId);
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
        requireReview(booking);
        HorseInsurance insurance = activeInsurance(bookingId, insuranceId);
        InsuranceReviewPolicy.validateConfirmation(insurance, booking.getRequestedPickupDate(), departureDate(booking),
                clock.instant());
        if (insurance.getCoverageOption() != InsuranceCoverageOption.NONE) {
            insurer(insurance.getInsurerParty().getId(), businessDate(clock.instant()));
        }
        insurance.setConfirmedBy(reviewer(username));
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
        requireReview(booking);
        HorseInsurance insurance = activeInsurance(bookingId, insuranceId);
        InsuranceReviewPolicy.requireStatus(insurance, HorseInsuranceStatus.CONFIRMED);
        String note = BookingReviewPolicy.requireReason(reason);
        Staff actor = reviewer(username);
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
        requireReview(booking);
        HorseInsurance insurance = activeInsurance(bookingId, insuranceId);
        InsuranceReviewPolicy.requirePersonal(insurance);
        InsuranceReviewPolicy.requireStatus(insurance, HorseInsuranceStatus.DRAFT);
        String note = BookingReviewPolicy.requireReason(reason);
        Staff actor = reviewer(username);
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
        Staff recipient = insurance.getRevisionRequestedBy();
        if (recipient != null) {
            notifications.notify(List.of(recipient.getUserId()), bookingId, "Hợp đồng bảo hiểm đã bổ sung",
                    "Kiểm tra lại chứng từ bảo hiểm trong chi tiết đơn.", null);
        }
    }

    private static LocalDate departureDate(Booking booking) {
        return booking.getDeparture() == null ? null : booking.getDeparture().getDepartureDate();
    }

    private static void requireReview(Booking booking) {
        BookingReviewPolicy.requireStatus(booking.getStatus(), BookingStatus.UNDER_REVIEW, BookingStatus.NEEDS_RESUBMISSION);
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

    private Staff reviewer(String username) {
        return staff.findByUser_Username(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ nhân viên."));
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
