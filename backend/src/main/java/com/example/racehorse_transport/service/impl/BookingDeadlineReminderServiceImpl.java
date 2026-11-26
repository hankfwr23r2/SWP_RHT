package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingStatusHistory;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.policy.BookingAssignmentPolicy;
import com.example.racehorse_transport.policy.BookingDeadlinePolicy;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.BookingDeadlineReminderService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingDeadlineReminderServiceImpl implements BookingDeadlineReminderService {
    private final BookingRepository bookings;
    private final BookingStatusHistoryRepository history;
    private final StaffRepository staff;
    private final SystemConfigService config;
    private final NotificationService notifications;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<Long> overdueBookingIds() {
        Instant now = clock.instant();
        var ids = new TreeSet<Long>();
        for (Step step : Step.values()) {
            Instant cutoff = cutoff(step, now);
            ids.addAll(step == Step.INTAKE
                    ? bookings.findIdsByStatusAndCreatedAtThrough(step.status, cutoff)
                    : history.findBookingIdsWithLatestStatusEntryThrough(step.status, cutoff));
        }
        return List.copyOf(ids);
    }

    @Override
    @Transactional
    public void remind(Long bookingId) {
        // Cùng khóa với đổi trạng thái đơn và phân công chuyên viên.
        var booking = bookings.findForUpdate(bookingId).orElse(null);
        if (booking == null) return;
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        for (Step step : Step.values()) {
            if (booking.getStatus() == step.status && isOverdue(booking, step, now)) {
                boolean review = step == Step.REVIEW;
                String path = review ? "/specialist/verification/" : "/manager/orders/";
                send(booking, step.title, path + bookingId, review, now);
                break;
            }
        }
    }

    private boolean isOverdue(Booking booking, Step step, Instant now) {
        Instant startedAt = step == Step.INTAKE ? booking.getCreatedAt()
                : history.findFirstByBooking_IdAndToStatusOrderByChangedAtDescIdDesc(booking.getId(), step.status)
                        .map(BookingStatusHistory::getChangedAt).orElse(null);
        return startedAt != null && !startedAt.isAfter(cutoff(step, now));
    }

    private Instant cutoff(Step step, Instant now) {
        return BookingDeadlinePolicy.cutoff(now, config.getInt(step.setting));
    }

    private void send(Booking booking, String title, String path, boolean review, Instant now) {
        var day = BookingDeadlinePolicy.dayOf(now);
        if (bookings.hasNotificationBetween(booking.getId(), title, day.start(), day.end())) return;
        var recipients = review ? assignedSpecialist(booking)
                : staff.findActiveForRole("MANAGER").stream().map(Staff::getUserId).toList();
        if (!recipients.isEmpty()) {
            notifications.notifyAt(recipients, booking.getId(), title, booking.getCode(), path, now);
        }
    }

    private static List<Long> assignedSpecialist(Booking booking) {
        var specialist = booking.getSpecialist();
        return BookingAssignmentPolicy.isAvailable(specialist) ? List.of(specialist.getUserId()) : List.of();
    }

    private enum Step {
        INTAKE(BookingStatus.PENDING_INTAKE, "BOOKING_INTAKE_DUE_HOURS", "Đơn quá hạn tiếp nhận"),
        REVIEW(BookingStatus.UNDER_REVIEW, "BOOKING_REVIEW_DUE_HOURS", "Đơn quá hạn thẩm định"),
        QUOTE(BookingStatus.QUOTING, "BOOKING_QUOTE_DUE_HOURS", "Đơn quá hạn gửi báo giá");

        private final BookingStatus status;
        private final String setting;
        private final String title;

        Step(BookingStatus status, String setting, String title) {
            this.status = status;
            this.setting = setting;
            this.title = title;
        }
    }
}
