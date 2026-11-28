package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.enums.TransportMode;
import com.example.racehorse_transport.dto.booking.BookingMergeSuggestionResponseDto;
import com.example.racehorse_transport.dto.booking.BookingRescheduleRequestDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.BookingStatusHistory;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import com.example.racehorse_transport.entity.enums.InsuranceCoverageOption;
import com.example.racehorse_transport.entity.enums.ScheduleChangeCause;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingSubmissionPolicy;
import com.example.racehorse_transport.policy.BalanceHoldPolicy;
import com.example.racehorse_transport.policy.DepartureCapacityPolicy;
import com.example.racehorse_transport.policy.DepartureDecisionPolicy;
import com.example.racehorse_transport.policy.DepartureMergePolicy;
import com.example.racehorse_transport.policy.InsuranceReviewPolicy;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.HorseInsuranceRepository;
import com.example.racehorse_transport.service.BookingRescheduleService;
import com.example.racehorse_transport.service.BookingRoutingService;
import com.example.racehorse_transport.service.BookingStatusService;
import com.example.racehorse_transport.service.DepartureService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Thứ tự khóa dùng chung: đơn trước, chuyến sau (chuyến theo id tăng dần).
@Service
@RequiredArgsConstructor
public class BookingRescheduleServiceImpl implements BookingRescheduleService {

    private final BookingRepository bookings;
    private final BookingHorseRepository horses;
    private final BookingRoutingService routing;
    private final BookingStatusService statuses;
    private final DepartureService departures;
    private final NotificationService notifications;
    private final SystemConfigService config;
    private final Clock clock;
    private final BookingStatusHistoryRepository history;
    private final HorseInsuranceRepository insurance;

    @Override
    @Transactional(readOnly = true)
    public Optional<BookingMergeSuggestionResponseDto> mergeSuggestion(Long bookingId, String username) {
        Booking booking = bookings.findByIdAndCustomer_User_Username(bookingId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        if (booking.getStatus() != BookingStatus.SCHEDULE_CHANGE_REQUIRED) {
            return Optional.empty();
        }
        Departure source = history.findFirstByBooking_IdAndToStatusOrderByChangedAtDescIdDesc(bookingId, BookingStatus.SCHEDULE_CHANGE_REQUIRED)
                .map(BookingStatusHistory::getPreviousDeparture)
                .orElse(null);
        Departure target = source == null ? null : source.getMergedIntoDeparture();
        if (target == null) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        LocalDate holdThrough = BookingSubmissionPolicy.priceHoldThrough(source.getDepartureDate(), config.getInt("PRICE_HOLD_DAYS"));
        LocalDate pickupDate = BookingSubmissionPolicy.latestPickupDate(booking.getBookingType(), target.getDepartureDate(), config.timeRulesSkipped());
        boolean privateMode = booking.getTransportMode() == TransportMode.PRIVATE;
        int stalls = seatsFor(activeHorses(bookingId));
        long sellableCapacity = privateMode ? stalls : departures.sellableCapacity(target);
        if (!DepartureMergePolicy.withinPriceHold(source, target, holdThrough)
                || pickupDate.isBefore(BookingSubmissionPolicy.businessDate(now))
                || DepartureCapacityPolicy.reasonNotReservable(target.getStatus(), target.getBookingCutoffAt(), now,
                        sellableCapacity, privateMode ? 0 : target.getBookedStallCount(), stalls).isPresent()
                || (privateMode && !departures.hasPrivateVehicle(target, stalls))) {
            return Optional.empty();
        }
        return Optional.of(BookingMergeSuggestionResponseDto.from(target, pickupDate,
                privateMode ? com.example.racehorse_transport.entity.enums.DepartureAvailability.AVAILABLE
                        : departures.availability(target, sellableCapacity - target.getStallCapacity())));
    }

    @Override
    @Transactional
    public BookingResponseDto reschedule(Long bookingId, String username, BookingRescheduleRequestDto request) {
        Booking booking = bookings.findForUpdate(bookingId)
                .filter(row -> row.getCustomer().getUser().getUsername().equals(username))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        if (booking.getStatus() != BookingStatus.SCHEDULE_CHANGE_REQUIRED) {
            throw BookingRuleException.conflict("Chỉ đổi chuyến khi đơn đang chờ chọn lại lịch.");
        }
        bookings.findReturnedBalanceHoldAt(bookingId).or(() ->
                history.findFirstByBooking_IdOrderByChangedAtDescIdDesc(bookingId)
                .filter(row -> BalanceHoldPolicy.RETURNED_AT_PICKUP.equals(row.getReason()))
                .map(BookingStatusHistory::getChangedAt))
                .ifPresent(returnedAt -> {
                    if (BalanceHoldPolicy.reached(clock.instant(), returnedAt, config.getInt("PRICE_HOLD_DAYS"),
                            java.time.temporal.ChronoUnit.DAYS)) {
                        throw BookingRuleException.conflict("Đã hết hạn chọn lịch sau khi nhận lại ngựa.");
                    }
                    if (bookings.hasUnbilledHoldingReturn(bookingId)) {
                        throw BookingRuleException.conflict("Phí giữ đang được chốt. Vui lòng kiểm tra lại trước khi chọn lịch đón.");
                    }
                    if (bookings.hasOutstandingPickupReturnPayments(bookingId)) {
                        throw BookingRuleException.conflict("Vui lòng thanh toán đủ số dư và phí giữ trước khi chọn lịch đón lại.");
                    }
                });
        // Vận đơn chỉ được cấp sau khi khách đặt cọc.
        boolean deposited = booking.getWaybillNumber() != null;
        LocalDate pickupDate = request.requestedPickupDate();
        if (pickupDate.isBefore(BookingSubmissionPolicy.businessDate(clock.instant()))) {
            throw new BookingRuleException("Ngày đón không được ở quá khứ.");
        }
        Long oldId = booking.getDeparture() == null ? null : booking.getDeparture().getId();
        if (request.departureId().equals(oldId)) {
            throw BookingRuleException.conflict("Chọn chuyến khác chuyến hiện tại.");
        }
        var active = activeHorses(bookingId);
        int seats = seatsFor(active);
        if (seats == 0) {
            throw BookingRuleException.conflict("Đơn không có ngựa đang hoạt động.");
        }
        booking.setRequestedPickupDate(pickupDate);
        Departure replacement = routing.lockReplacement(booking, request.departureId());
        if (deposited) {
            validatePaidReplacement(bookingId, replacement, pickupDate, active);
        }
        if (booking.getTransportMode() == TransportMode.PRIVATE) {
            if (oldId != null) {
                departures.releasePrivate(oldId, booking.getId());
            }
            departures.reservePrivate(replacement.getId(), booking, seats);
        } else {
            departures.reserve(replacement.getId(), seats);
            if (oldId != null) {
                departures.release(oldId, seats);
            }
        }
        statuses.clearPlannedTransport(booking.getId());
        booking.setDeparture(replacement);
        statuses.transition(bookingId, deposited ? BookingStatus.CONFIRMED : BookingStatus.UNDER_REVIEW,
                "Khách chọn lại chuyến ngày " + replacement.getDepartureDate(), booking.getCustomer().getUser().getId());
        return BookingResponseDto.from(booking);
    }

    private void validatePaidReplacement(Long bookingId, Departure replacement, LocalDate pickupDate, List<BookingHorse> active) {
        var scheduleChange = history.findFirstByBooking_IdAndToStatusOrderByChangedAtDescIdDesc(
                bookingId, BookingStatus.SCHEDULE_CHANGE_REQUIRED)
                .filter(row -> row.getCauseType() != null && row.getPreviousDeparture() != null)
                .orElseThrow(() -> BookingRuleException.conflict("Thiếu lịch sử chuyến vừa lỡ hoặc nguyên nhân đổi chuyến."));
        var previous = scheduleChange.getPreviousDeparture();
        if (replacement.getId().equals(previous.getId())
                || !replacement.getRoute().getId().equals(previous.getRoute().getId())) {
            throw BookingRuleException.conflict("Chọn chuyến khác cùng tuyến để giữ báo giá.");
        }
        BookingSubmissionPolicy.validatePriceHold(previous.getDepartureDate(), replacement.getDepartureDate(),
                config.getInt("PRICE_HOLD_DAYS"));
        if (scheduleChange.getCauseType() == ScheduleChangeCause.CUSTOMER
                && history.countByBooking_IdAndToStatusAndCauseType(bookingId,
                        BookingStatus.SCHEDULE_CHANGE_REQUIRED, ScheduleChangeCause.CUSTOMER) > 1) {
            throw BookingRuleException.conflict("Đơn đã dùng lượt đổi chuyến miễn phí do khách.");
        }
        validateInsurance(active, pickupDate, replacement.getDepartureDate());
    }

    private void validateInsurance(List<BookingHorse> active, LocalDate pickupDate, LocalDate departureDate) {
        var activeHorseIds = active.stream().map(BookingHorse::getId).toList();
        insurance.findForDetail(activeHorseIds).stream()
                .filter(row -> row.getCoverageOption() == InsuranceCoverageOption.PERSONAL)
                .forEach(row -> {
                    InsuranceReviewPolicy.requireStatus(row, HorseInsuranceStatus.CONFIRMED);
                    InsuranceReviewPolicy.validatePeriod(row.getValidFrom(), row.getValidTo(), pickupDate, departureDate);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isDirect(Booking booking) {
        return !routing.requiresDeparture(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireDirectSchedule(Booking booking, LocalDate pickupDate) {
        if (!isDirect(booking)) {
            throw BookingRuleException.conflict("Đơn đi theo chuyến, cần chọn chuyến mới.");
        }
        routing.requireDirectLeadTime(booking, pickupDate);
        validateInsurance(activeHorses(booking.getId()), pickupDate, null);
    }

    @Override
    @Transactional
    public boolean releaseAtDecision(Long bookingId) {
        Booking booking = bookings.findForUpdate(bookingId).orElse(null);
        if (booking == null || booking.getDeparture() == null
                || !DepartureDecisionPolicy.RELEASABLE.contains(booking.getStatus())) {
            return false;
        }
        Departure departure = booking.getDeparture();
        Instant decisionAt = DepartureDecisionPolicy.decisionAt(departure.getPlannedDepartureAt(),
                config.getInt("DEPARTURE_DECISION_HOURS"));
        if (clock.instant().isBefore(decisionAt)) {
            return false;
        }
        int seats = seatsFor(activeHorses(bookingId));
        if (seats > 0) {
            if (booking.getTransportMode() == TransportMode.PRIVATE) {
                departures.releasePrivate(departure.getId(), booking.getId());
            } else {
                departures.release(departure.getId(), seats);
            }
        }
        booking.setDeparture(null);
        String reason = "Chưa hoàn tất báo giá trước mốc quyết định của chuyến " + departure.getRoute().getName()
                + " ngày " + departure.getDepartureDate();
        statuses.requireScheduleChange(bookingId, departure, null, reason, null);
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), bookingId, "Cần chọn lại chuyến",
                reason + ". Chọn chuyến khác hoặc hủy đơn không mất phí.", null);
        return true;
    }

    private List<BookingHorse> activeHorses(Long bookingId) {
        return horses.findForDetail(bookingId).stream()
                .filter(horse -> horse.getStatus() == BookingHorseStatus.ACTIVE).toList();
    }

    private static int seatsFor(List<BookingHorse> active) {
        return StallSeatPolicy.total(active.stream().map(horse -> horse.getStallType().getCode()).toList());
    }
}
