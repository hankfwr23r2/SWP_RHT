package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingCancellationResponseDto;
import com.example.racehorse_transport.dto.booking.BookingDelayResponseDto;
import com.example.racehorse_transport.dto.booking.BookingRescheduleRequestDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.booking.CancelBookingRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingStatusHistory;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.ScheduleChangeCause;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingCancellationPolicy;
import com.example.racehorse_transport.policy.BookingStatusPolicy;
import com.example.racehorse_transport.policy.DepartureDecisionPolicy;
import com.example.racehorse_transport.policy.DepartureDelayPolicy;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.service.BookingCancellationService;
import com.example.racehorse_transport.service.BookingDelayService;
import com.example.racehorse_transport.service.BookingRescheduleService;
import com.example.racehorse_transport.service.BookingStatusService;
import com.example.racehorse_transport.service.DepartureDelayService;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingDelayServiceImpl implements BookingDelayService {
    private final BookingRepository bookings;
    private final BookingStatusHistoryRepository history;
    private final DepartureRepository departures;
    private final DepartureDelayService delays;
    private final BookingStatusService statuses;
    private final BookingRescheduleService reschedules;
    private final BookingCancellationService cancellations;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<BookingDelayResponseDto> view(Long bookingId, String username) {
        Booking booking = owned(bookingId, username);
        return affectedDeparture(booking).filter(departure -> departure.getDelayedAt() != null)
                .map(departure -> options(booking, departure)).stream().toList();
    }

    @Override
    @Transactional
    public BookingResponseDto reschedule(Long bookingId, String username, BookingRescheduleRequestDto request) {
        var booking = lockOwned(bookingId, username);
        var departure = lockAffectedDeparture(booking, request.departureId());
        if (request.departureId().equals(departure.getId()) || !eligibility(booking, departure).canRescheduleFree()) {
            throw BookingRuleException.conflict("Đơn chưa được đổi miễn phí do chuyến chậm hoặc cần chọn chuyến khác.");
        }
        if (booking.getStatus() != BookingStatus.SCHEDULE_CHANGE_REQUIRED) {
            statuses.requireScheduleChange(bookingId, departure, ScheduleChangeCause.COMPANY,
                    "Khách chọn chuyến khác vì chuyến cũ bị chậm.", booking.getCustomer().getUser().getId());
        }
        return reschedules.reschedule(bookingId, username, request);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingCancellationResponseDto refundPreview(Long bookingId, String username) {
        var booking = owned(bookingId, username);
        requireRefund(booking, affectedDeparture(booking).orElseThrow(BookingDelayServiceImpl::notEligible));
        return cancellations.previewForDelayedDeparture(booking);
    }

    @Override
    @Transactional
    public BookingCancellationResponseDto refund(Long bookingId, String username, CancelBookingRequestDto request) {
        var booking = lockOwned(bookingId, username);
        requireRefund(booking, lockAffectedDeparture(booking, null));
        return cancellations.cancelForDelayedDeparture(bookingId, username, request);
    }

    private BookingDelayResponseDto options(Booking booking, Departure departure) {
        var eligibility = eligibility(booking, departure);
        boolean canRefund = eligibility.canRequestRefund() && cancellations.previewForDelayedDeparture(booking).allowed();
        return BookingDelayResponseDto.from(departure, eligibility.canRescheduleFree(), canRefund, eligibility.requiresManagerContact());
    }

    private DelayEligibility eligibility(Booking booking, Departure departure) {
        boolean open = DepartureDelayPolicy.notStarted(departure.getStatus()) || departure.getAutoCancelledAt() != null;
        boolean affected = open && departure.getDelayedAt() != null;
        boolean received = affected && (DepartureDecisionPolicy.blocksCancellation(booking.getStatus())
                || bookings.hasReceivedHorses(booking.getId()));
        boolean awaitingChange = booking.getStatus() == BookingStatus.SCHEDULE_CHANGE_REQUIRED;
        if (!affected || received) {
            return new DelayEligibility(false, false, received);
        }
        var duration = DepartureDelayPolicy.delay(departure.getPlannedDepartureAt(), departure.getRevisedDepartureAt(), clock.instant());
        var limits = delays.thresholds();
        boolean canChange = (awaitingChange || BookingStatusPolicy.canTransition(booking.getStatus(), BookingStatus.SCHEDULE_CHANGE_REQUIRED))
                && (departure.getAutoCancelledAt() != null || DepartureDelayPolicy.permitsReschedule(duration, limits));
        boolean canRefund = BookingCancellationPolicy.companyCanCancel(booking.getStatus())
                && (departure.getAutoCancelledAt() != null || DepartureDelayPolicy.permitsRefund(duration, limits));
        return new DelayEligibility(canChange, canRefund, false);
    }

    private Optional<Departure> affectedDeparture(Booking booking) {
        if (booking.getDeparture() != null) {
            return Optional.of(booking.getDeparture());
        }
        if (booking.getStatus() != BookingStatus.SCHEDULE_CHANGE_REQUIRED) {
            return Optional.empty();
        }
        return history.findFirstByBooking_IdAndToStatusOrderByChangedAtDescIdDesc(booking.getId(), BookingStatus.SCHEDULE_CHANGE_REQUIRED)
                .filter(change -> change.getCauseType() == ScheduleChangeCause.COMPANY)
                .map(BookingStatusHistory::getPreviousDeparture)
                .filter(departure -> departure.getAutoCancelledAt() != null);
    }

    private Departure lockAffectedDeparture(Booking booking, Long replacementId) {
        Long departureId = affectedDeparture(booking).orElseThrow(BookingDelayServiceImpl::notEligible).getId();
        if (replacementId != null && replacementId < departureId) {
            lockDeparture(replacementId);
        }
        Departure locked = lockDeparture(departureId);
        if (replacementId != null && replacementId > departureId) {
            lockDeparture(replacementId);
        }
        return locked;
    }

    private Departure lockDeparture(Long id) {
        return departures.findForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyến " + id));
    }

    private void requireRefund(Booking booking, Departure departure) {
        if (!eligibility(booking, departure).canRequestRefund()) {
            throw notEligible();
        }
    }

    private Booking owned(Long bookingId, String username) {
        return bookings.findByIdAndCustomer_User_Username(bookingId, username).orElseThrow(BookingDelayServiceImpl::notFound);
    }

    private Booking lockOwned(Long bookingId, String username) {
        return bookings.findForUpdate(bookingId)
                .filter(booking -> username.equals(booking.getCustomer().getUser().getUsername()))
                .orElseThrow(BookingDelayServiceImpl::notFound);
    }

    private static BookingRuleException notEligible() {
        return BookingRuleException.conflict("Đơn chưa đủ điều kiện hoàn tiền do chuyến chậm. Nếu đã giao ngựa, vui lòng đợi quản lý liên hệ.");
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Không tìm thấy đơn.");
    }

    private record DelayEligibility(boolean canRescheduleFree, boolean canRequestRefund, boolean requiresManagerContact) {}
}
