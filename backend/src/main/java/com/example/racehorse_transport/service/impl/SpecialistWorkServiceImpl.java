package com.example.racehorse_transport.service.impl;

import static com.example.racehorse_transport.entity.enums.BookingStatus.*;
import static com.example.racehorse_transport.entity.enums.InsuranceCoverageOption.*;

import com.example.racehorse_transport.dto.booking.BookingSummaryResponseDto;
import com.example.racehorse_transport.dto.specialist.SpecialistWorkResponseDto;
import com.example.racehorse_transport.dto.specialist.SpecialistWorkResponseDto.*;
import com.example.racehorse_transport.dto.specialist.SpecialistWorkSummaryResponseDto;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.InsuranceCoverageOption;
import com.example.racehorse_transport.policy.SpecialistWorkPolicy;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingHorseRepository.SpecialistHorse;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.service.BookingQueryService;
import com.example.racehorse_transport.service.DocumentReviewService;
import com.example.racehorse_transport.service.SpecialistWorkService;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpecialistWorkServiceImpl implements SpecialistWorkService {
    private static final Set<BookingStatus> PENDING_DOCUMENT_STATUSES = Set.of(QUOTING, AWAITING_DEPOSIT, CONFIRMED);
    private static final Set<BookingStatus> INSURANCE_WORK_STATUSES = Set.of(UNDER_REVIEW, NEEDS_RESUBMISSION, QUOTING,
            AWAITING_DEPOSIT, CONFIRMED, SCHEDULE_CHANGE_REQUIRED);

    private final BookingQueryService bookings;
    private final DocumentReviewService documents;
    private final BookingRepository bookingRows;
    private final BookingHorseRepository bookingHorses;

    @Override
    @Transactional(readOnly = true)
    public List<SpecialistWorkResponseDto> queue(String username, boolean all) {
        var statuses = EnumSet.allOf(BookingStatus.class);
        statuses.removeAll(EnumSet.of(CANCELLED, REJECTED));
        List<SpecialistWorkResponseDto> result = new ArrayList<>();
        for (int page = 0; ; page++) {
            var batch = bookings.summaries(statuses, page, all ? null : username);
            if (batch.isEmpty()) return result;
            var horseIds = batch.stream().map(BookingSummaryResponseDto::id).toList();
            var horses = bookingHorses.findSpecialistHorses(horseIds).stream()
                    .collect(Collectors.groupingBy(SpecialistHorse::getBookingId));
            batch.forEach(booking -> result.add(row(booking, horses.getOrDefault(booking.id(), List.of()))));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SpecialistWorkSummaryResponseDto summary(String username) {
        long underReview = bookingRows.countBySpecialist_User_UsernameAndStatus(username, UNDER_REVIEW);
        long waiting = bookingRows.countBySpecialist_User_UsernameAndStatus(username, NEEDS_RESUBMISSION);
        var verificationIds = new ArrayList<Long>();
        var legalIds = new ArrayList<Long>();
        var pending = bookingRows.findBySpecialist_User_UsernameAndStatusInOrderByCreatedAtAscIdAsc(username,
                EnumSet.of(QUOTING, AWAITING_DEPOSIT, CONFIRMED, SCHEDULE_CHANGE_REQUIRED));
        for (var booking : pending) {
            var checklist = documents.checklist(booking.getId(), null);
            if (PENDING_DOCUMENT_STATUSES.contains(booking.getStatus())
                    && SpecialistWorkPolicy.verification(booking.getStatus(), checklist) == Verification.TO_REVIEW) {
                verificationIds.add(booking.getId());
            }
            var legal = SpecialistWorkPolicy.legal(checklist);
            if (legal != null && legal != Legal.COMPLETE && showLegal(booking.getStatus(), legal)) {
                legalIds.add(booking.getId());
            }
        }
        return new SpecialistWorkSummaryResponseDto(underReview + countWork(username, verificationIds), waiting,
                countWork(username, legalIds), bookingRows.countSpecialistInsuranceWork(username, INSURANCE_WORK_STATUSES));
    }

    private long countWork(String username, List<Long> ids) {
        return ids.isEmpty() ? 0 : bookingRows.countSpecialistWork(username, ids);
    }

    private SpecialistWorkResponseDto row(BookingSummaryResponseDto booking, List<SpecialistHorse> horses) {
        var checklist = documents.checklist(booking.id(), null);
        var legal = SpecialistWorkPolicy.legal(checklist);
        var insurance = SpecialistWorkPolicy.insurance(horses.stream().map(SpecialistHorse::getInsuranceStatus).toList());
        return new SpecialistWorkResponseDto(booking, horses.stream().map(SpecialistHorse::getHorseName).toList(),
                SpecialistWorkPolicy.verification(booking.status(), checklist),
                showLegal(booking.status(), legal) ? legal : null,
                insurance == Insurance.CONFIRMED || INSURANCE_WORK_STATUSES.contains(booking.status()) ? insurance : null,
                booking.departureDate() == null ? booking.requestedPickupDate() : booking.departureDate(),
                count(horses, ARRANGED), count(horses, PERSONAL), count(horses, NONE));
    }

    private static boolean showLegal(BookingStatus status, Legal legal) {
        return legal == Legal.COMPLETE || status == CONFIRMED || legal == Legal.IN_PROGRESS && status == SCHEDULE_CHANGE_REQUIRED;
    }

    private static long count(List<SpecialistHorse> horses, InsuranceCoverageOption option) {
        return horses.stream().filter(horse -> horse.getCoverageOption() == option).count();
    }
}
