package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingRescheduleRequestDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingSubmissionPolicy;
import com.example.racehorse_transport.policy.DepartureDecisionPolicy;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingRepository;
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

    @Override
    @Transactional
    public BookingResponseDto reschedule(Long bookingId, String username, BookingRescheduleRequestDto request) {
        Booking booking = bookings.findForUpdate(bookingId)
                .filter(row -> row.getCustomer().getUser().getUsername().equals(username))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        if (booking.getStatus() != BookingStatus.SCHEDULE_CHANGE_REQUIRED) {
            throw BookingRuleException.conflict("Chỉ đổi chuyến khi đơn đang chờ chọn lại lịch.");
        }
        // Vận đơn chỉ được cấp sau khi khách đặt cọc
        if (booking.getWaybillNumber() != null) {
            throw BookingRuleException.conflict("Đơn đã đặt cọc, quản lý sẽ xử lý việc đổi chuyến.");
        }
        LocalDate pickupDate = request.requestedPickupDate();
        if (pickupDate.isBefore(BookingSubmissionPolicy.businessDate(clock.instant()))) {
            throw new BookingRuleException("Ngày đón không được ở quá khứ.");
        }
        Long oldId = booking.getDeparture() == null ? null : booking.getDeparture().getId();
        if (request.departureId().equals(oldId)) {
            throw BookingRuleException.conflict("Chọn chuyến khác chuyến hiện tại.");
        }
        int seats = activeSeats(bookingId);
        if (seats == 0) {
            throw BookingRuleException.conflict("Đơn không có ngựa đang hoạt động.");
        }
        booking.setRequestedPickupDate(pickupDate);
        Departure replacement = routing.lockReplacement(booking, request.departureId());
        departures.reserve(replacement.getId(), seats);
        if (oldId != null) {
            departures.release(oldId, seats);
        }
        booking.setDeparture(replacement);
        statuses.transition(bookingId, BookingStatus.UNDER_REVIEW,
                "Khách chọn lại chuyến ngày " + replacement.getDepartureDate(), booking.getCustomer().getUser().getId());
        return BookingResponseDto.from(booking);
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
        int seats = activeSeats(bookingId);
        if (seats > 0) {
            departures.release(departure.getId(), seats);
        }
        booking.setDeparture(null);
        String reason = "Chưa hoàn tất báo giá trước mốc quyết định của chuyến " + departure.getRoute().getName()
                + " ngày " + departure.getDepartureDate();
        statuses.transition(bookingId, BookingStatus.SCHEDULE_CHANGE_REQUIRED, reason, null);
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), bookingId, "Cần chọn lại chuyến",
                reason + ". Chọn chuyến khác hoặc hủy đơn không mất phí.", null);
        return true;
    }

    private int activeSeats(Long bookingId) {
        return StallSeatPolicy.total(horses.findForDetail(bookingId).stream()
                .filter(horse -> horse.getStatus() == BookingHorseStatus.ACTIVE)
                .map(horse -> horse.getStallType().getCode()).toList());
    }
}
