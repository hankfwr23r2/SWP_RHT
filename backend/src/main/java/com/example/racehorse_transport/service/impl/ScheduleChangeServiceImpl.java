package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.booking.ReportDocumentDelayRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.entity.enums.ScheduleChangeCause;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.BookingCancellationPolicy;
import com.example.racehorse_transport.policy.BookingCancellationPolicy.ScheduleCancellation;
import com.example.racehorse_transport.policy.DepartureDecisionPolicy;
import com.example.racehorse_transport.policy.StallSeatPolicy;
import com.example.racehorse_transport.repository.BookingHorseRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.service.BookingStatusService;
import com.example.racehorse_transport.service.DepartureService;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.service.DocumentCompletenessService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.ScheduleChangeService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScheduleChangeServiceImpl implements ScheduleChangeService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String REMINDER_TITLE = "Bổ sung giấy tờ trước chuyến";

    private final BookingRepository bookings;
    private final DocumentCompletenessService documents;
    private final SystemConfigService config;
    private final NotificationService notifications;
    private final Clock clock;
    private final BookingHorseRepository horses;
    private final DepartureService departures;
    private final BookingStatusService statuses;
    private final BookingStatusHistoryRepository history;

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponseDto> missingDocuments() {
        return bookings.findWithDeparture(BookingStatus.CONFIRMED).stream()
                .filter(booking -> !documents.isComplete(booking.getId()))
                .map(BookingResponseDto::from).toList();
    }

    @Override
    @Transactional
    public BookingResponseDto reportDocumentDelay(Long bookingId, String username, ReportDocumentDelayRequestDto request) {
        var booking = bookings.findForUpdate(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
        var departure = booking.getDeparture();
        if (request.cause() == null || request.reason() == null || request.reason().isBlank()
                || request.reason().length() > 500) {
            throw new BookingRuleException("Cần nhập nguyên nhân và lý do thiếu giấy.");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED || departure == null
                || departure.getStatus() == DepartureStatus.CANCELLED
                || bookings.hasReceivedHorses(bookingId) || documents.isComplete(bookingId)) {
            throw BookingRuleException.conflict("Chỉ báo trễ giấy cho đơn đã cọc, còn chuyến, chưa nhận ngựa và thiếu giấy.");
        }
        var decisionAt = DepartureDecisionPolicy.decisionAt(departure.getPlannedDepartureAt(),
                config.getInt("DEPARTURE_DECISION_HOURS"));
        if (clock.instant().isBefore(decisionAt)) {
            throw BookingRuleException.conflict("Chưa tới mốc quyết định của chuyến.");
        }
        int seats = StallSeatPolicy.total(horses.findForDetail(bookingId).stream()
                .filter(horse -> horse.getStatus() == BookingHorseStatus.ACTIVE)
                .map(horse -> horse.getStallType().getCode()).toList());
        if (seats > 0) {
            departures.release(departure.getId(), seats);
        }
        booking.setDeparture(null);
        statuses.requireScheduleChangeForUser(bookingId, departure, request.cause(), request.reason(), username);
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), bookingId, "Cần chọn lại chuyến",
                "Đơn " + booking.getCode() + " không kịp giấy tờ cho chuyến ngày " + departure.getDepartureDate()
                        + ". Lý do: " + request.reason().strip(), null);
        return BookingResponseDto.from(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleCancellation cancellationTerms(Long bookingId) {
        var cause = history.findFirstByBooking_IdAndToStatusOrderByChangedAtDescIdDesc(
                bookingId, BookingStatus.SCHEDULE_CHANGE_REQUIRED).map(row -> row.getCauseType()).orElse(null);
        if (cause != ScheduleChangeCause.AUTHORITY) {
            return BookingCancellationPolicy.scheduleCancellation(cause, 0, 1);
        }
        long misses = history.countByBooking_IdAndToStatusAndCauseType(
                bookingId, BookingStatus.SCHEDULE_CHANGE_REQUIRED, ScheduleChangeCause.AUTHORITY);
        return BookingCancellationPolicy.scheduleCancellation(cause, misses, config.getInt("AUTHORITY_DELAY_REFUND_MISSES"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> reminderCandidates() {
        Instant now = clock.instant();
        return bookings.findIdsForDocumentReminder(BookingStatus.CONFIRMED, now, reminderCutoff(now));
    }

    @Override
    @Transactional
    public boolean remind(Long bookingId) {
        var booking = bookings.findForUpdate(bookingId).orElse(null);
        Instant now = clock.instant();
        if (booking == null || booking.getStatus() != BookingStatus.CONFIRMED || booking.getDeparture() == null
                || !insideReminderWindow(booking, now) || documents.isComplete(bookingId)) {
            return false;
        }
        var today = now.atZone(BUSINESS_ZONE).toLocalDate();
        Instant start = today.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        if (bookings.hasNotificationBetween(bookingId, REMINDER_TITLE, start, end)) {
            return false;
        }
        notifications.notify(List.of(booking.getCustomer().getUser().getId()), bookingId, REMINDER_TITLE,
                "Đơn " + booking.getCode() + " còn thiếu giấy tờ cho chuyến ngày "
                        + booking.getDeparture().getDepartureDate() + ".", null);
        return true;
    }

    private boolean insideReminderWindow(Booking booking, Instant now) {
        Instant departureAt = booking.getDeparture().getPlannedDepartureAt();
        return !departureAt.isBefore(now) && !departureAt.isAfter(reminderCutoff(now));
    }

    private Instant reminderCutoff(Instant now) {
        return now.plus(config.getInt("DOCUMENT_REMINDER_DAYS"), ChronoUnit.DAYS);
    }
}
