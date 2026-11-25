package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingReviewPolicy;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.service.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.example.racehorse_transport.entity.enums.BookingHorseStatus.ACTIVE;
import static com.example.racehorse_transport.entity.enums.BookingStatus.*;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingReviewServiceImpl implements BookingReviewService {
    private final BookingRepository bookings;
    private final AppUserRepository users;
    private final BookingHorseRepository horses;
    private final InsuranceService insurance;
    private final BookingStatusService statuses;
    private final BookingRoutingService routing;
    private final DepartureService departures;
    private final DocumentCompletenessService documents;
    private final NotificationService notifications;
    private final BookingStatusHistoryRepository history;

    @Override
    public BookingResponseDto intake(Long id, String username) {
        Booking booking = lock(id, PENDING_INTAKE);
        return transition(booking, UNDER_REVIEW, null, actor(username));
    }

    @Override
    public BookingResponseDto returnToCustomer(Long id, String username, String reason) {
        Booking booking = lock(id, UNDER_REVIEW);
        String note = BookingReviewPolicy.requireReason(reason);
        BookingResponseDto result = transition(booking, NEEDS_RESUBMISSION, note, actor(username));
        notifyCustomer(booking, "Đơn cần bổ sung hồ sơ", note);
        return result;
    }

    @Override
    public BookingResponseDto resubmit(Long id, String username) {
        Booking booking = bookings.findForUpdate(id).orElseThrow(BookingReviewServiceImpl::missing);
        AppUser customer = booking.getCustomer().getUser();
        if (!customer.getUsername().equals(username)) {
            throw missing();
        }
        BookingReviewPolicy.requireStatus(booking.getStatus(), NEEDS_RESUBMISSION);
        var previous = history.findFirstByBooking_IdOrderByChangedAtDescIdDesc(id);
        BookingResponseDto result = transition(booking, UNDER_REVIEW, null, customer.getId());
        previous.filter(row -> row.getToStatus() == NEEDS_RESUBMISSION && row.getChangedBy() != null)
                .ifPresent(row -> notifications.notify(List.of(row.getChangedBy().getId()), id,
                        "Khách đã nộp lại hồ sơ", "Kiểm tra lại giấy tờ và bảo hiểm của đơn.", null));
        return result;
    }

    @Override
    public BookingResponseDto reject(Long id, String username, String reason) {
        Booking booking = lock(id, PENDING_INTAKE, UNDER_REVIEW);
        String note = BookingReviewPolicy.requireReason(reason);
        BookingResponseDto result = transition(booking, REJECTED, note, actor(username));
        int stalls = activeSeats(id);
        if (booking.getDeparture() != null && stalls > 0) {
            departures.release(booking.getDeparture().getId(), stalls);
        }
        notifyCustomer(booking, "Đơn bị từ chối", note);
        return result;
    }

    @Override
    public BookingResponseDto changeDeparture(Long id, Long departureId) {
        Booking booking = lock(id, UNDER_REVIEW, NEEDS_RESUBMISSION);
        Long oldId = booking.getDeparture() == null ? null : booking.getDeparture().getId();
        if (departureId.equals(oldId)) {
            throw BookingRuleException.conflict("Đơn đã giữ chỗ trên chuyến này.");
        }
        Departure replacement = routing.lockReplacement(booking, departureId);
        int stalls = activeSeats(id);
        if (stalls == 0) {
            throw BookingRuleException.conflict("Đơn không có ngựa đang hoạt động.");
        }
        departures.reserve(departureId, stalls);
        if (oldId != null) {
            departures.release(oldId, stalls);
        }
        statuses.clearPlannedTransport(booking.getId());
        booking.setDeparture(replacement);
        notifyCustomer(booking, "Đơn đã đổi chuyến", "Kiểm tra thông tin chuyến mới trong chi tiết đơn.");
        return BookingResponseDto.from(booking);
    }

    @Override
    public BookingResponseDto startQuoting(Long id, String username) {
        Booking booking = lock(id, UNDER_REVIEW);
        if (booking.getDeparture() == null && routing.requiresDeparture(booking)) {
            throw BookingRuleException.conflict("Đơn chưa có chuyến trục.");
        }
        if (!documents.isCompleteForQuoting(id)) {
            throw BookingRuleException.conflict("Đơn chưa đủ giấy tờ bắt buộc do khách hàng nộp.");
        }
        var active = horses.findForDetail(id).stream().filter(horse -> horse.getStatus() == ACTIVE).toList();
        insurance.validateForQuoting(booking, active);
        return transition(booking, QUOTING, null, actor(username));
    }

    private int activeSeats(Long id) {
        return StallSeatPolicy.total(horses.findByBooking_Id(id).stream()
                .filter(horse -> horse.getStatus() == ACTIVE)
                .map(horse -> horse.getStallType().getCode()).toList());
    }

    private Booking lock(Long id, BookingStatus... allowed) {
        Booking booking = bookings.findForUpdate(id).orElseThrow(BookingReviewServiceImpl::missing);
        BookingReviewPolicy.requireStatus(booking.getStatus(), allowed);
        return booking;
    }

    private Long actor(String username) {
        return users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản.")).getId();
    }

    private BookingResponseDto transition(Booking booking, BookingStatus next, String reason, Long actorId) {
        statuses.transition(booking.getId(), next, reason, actorId);
        return BookingResponseDto.from(booking);
    }

    private void notifyCustomer(Booking booking, String title, String message) {
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), booking.getId(), title, message, null);
    }

    private static ResourceNotFoundException missing() {
        return new ResourceNotFoundException("Không tìm thấy đơn.");
    }
}
