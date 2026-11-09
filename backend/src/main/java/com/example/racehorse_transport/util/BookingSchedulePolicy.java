package com.example.racehorse_transport.util;

import com.example.racehorse_transport.entity.Booking;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

/** Booking dates are calendar days in Vietnam, not midnight departure appointments. */
public final class BookingSchedulePolicy {
    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final String REQUIRED = "schedule_change_required";
    public static final Set<String> REVIEW_STATUSES = Set.of(
            "pending_intake", "under_review", "resubmission_requested", "pending_commercial",
            "awaiting_payment", "quote_expired");

    private BookingSchedulePolicy() { }

    public static Instant deadline(Booking booking) {
        if (booking.getDepartureDate() == null) return null;
        return booking.getDepartureDate().atZone(VIETNAM).toLocalDate().plusDays(1)
                .atStartOfDay(VIETNAM).toInstant();
    }

    public static boolean overdue(Booking booking, Instant now) {
        Instant deadline = deadline(booking);
        return deadline != null && !now.isBefore(deadline);
    }

    public static void requireCurrentSchedule(Booking booking, Instant now) {
        if (REQUIRED.equals(booking.getStatus()) || overdue(booking, now)) {
            throw new IllegalStateException("Ngày khởi hành dự kiến đã qua. Cần khách xác nhận lại lịch trước khi tiếp tục xử lý đơn.");
        }
    }
}
