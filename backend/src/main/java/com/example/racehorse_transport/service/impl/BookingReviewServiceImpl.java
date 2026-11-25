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
    private final BookingAssignmentService assignments;
    private final BookingHorseRepository horses;
    private final InsuranceService insurance;
    private final BookingStatusService statuses;
    private final BookingRoutingService routing;
    private final DepartureService departures;
    private final DocumentCompletenessService documents;
    private final NotificationService notifications;
    private final BookingStatusHistoryRepository history;

    @Override
    public BookingResponseDto intake(Long id, String username, Long specialistId) {
        Booking booking = lock(id, PENDING_INTAKE);
        assignments.assignAtIntake(booking, specialistId, username);
        return transition(booking, UNDER_REVIEW, null, actor(username));
    }

    @Override
    public BookingResponseDto returnToCustomer(Long id, String username, String reason) {
        Booking booking = lock(id, UNDER_REVIEW);
        String note = BookingReviewPolicy.requireReason(reason);
        BookingResponseDto result = transition(booking, NEEDS_RESUBMISSION, note, assignments.requireAssigned(booking, username).getUserId());
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
        BookingResponseDto result = transition(booking, UNDER_REVIEW, null, customer.getId());
        var recipients = assignments.recipients(booking);
        if (!recipients.isEmpty()) {
            notifications.notify(recipients, id, "Khách đã nộp lại hồ sơ",
                    "Kiểm tra lại giấy tờ và bảo hiểm của đơn.", assignments.reviewLink(booking));
        }
        return result;
    }

    @Override
    public BookingResponseDto reject(Long id, String username, String reason) {
        Booking booking = lock(id, PENDING_INTAKE, UNDER_REVIEW);
        String note = BookingReviewPolicy.requireReason(reason);
        AppUser actor = assignments.actor(username);
        if (booking.getStatus() == UNDER_REVIEW) {
            assignments.requireAssigned(booking, username);
        } else if (!"MANAGER".equals(actor.getRole().getCode())) {
            throw BookingRuleException.forbidden("Chỉ quản lý được từ chối đơn chờ tiếp nhận.");
        }
        BookingResponseDto result = transition(booking, REJECTED, note, actor.getId());
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
        var specialist = assignments.requireAssigned(booking, username);
        if (booking.getDeparture() == null && routing.requiresDeparture(booking)) {
            throw BookingRuleException.conflict("Đơn chưa có chuyến trục.");
        }
        if (!documents.isCompleteForQuoting(id)) {
            throw BookingRuleException.conflict("Đơn chưa đủ giấy tờ bắt buộc do khách hàng nộp.");
        }
        var active = horses.findForDetail(id).stream().filter(horse -> horse.getStatus() == ACTIVE).toList();
        insurance.validateForQuoting(booking, active);
        return transition(booking, QUOTING, null, specialist.getUserId());
    }

    @Override
    @Transactional
    public BookingResponseDto reopenExpiredQuote(Long id, String username) {
        var booking = lock(id, QUOTE_EXPIRED);
        assignments.requireAvailableForReopening(booking);
        var previous = history.findFirstByBooking_IdAndToStatusOrderByChangedAtDescIdDesc(id, QUOTE_EXPIRED)
                .map(row -> row.getPreviousDeparture()).orElse(booking.getDeparture());
        Long departureId = previous == null ? null : previous.getId();
        routing.validateReopenPickup(booking, departureId);
        Departure departure = departureId == null ? null : routing.lockReplacement(booking, departureId);
        booking.setDeparture(departure);
        if (!documents.isCompleteForQuoting(id)) {
            throw BookingRuleException.conflict("Đơn chưa đủ giấy tờ bắt buộc.");
        }
        var active = horses.findForDetail(id).stream().filter(horse -> horse.getStatus() == ACTIVE).toList();
        if (active.isEmpty()) {
            throw BookingRuleException.conflict("Đơn không có ngựa đang hoạt động.");
        }
        insurance.validateForQuoting(booking, active);
        if (departure != null) {
            int stalls = StallSeatPolicy.total(active.stream().map(horse -> horse.getStallType().getCode()).toList());
            departures.reserve(departure.getId(), stalls);
        }
        var result = transition(booking, QUOTING, "Mở lại báo giá hết hạn.", actor(username));
        notifyCustomer(booking, "Đã mở lại báo giá", "Quản lý đang chuẩn bị báo giá mới cho đơn.");
        return result;
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
        return assignments.actor(username).getId();
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
