package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.time.Duration;
import java.time.Instant;

public final class DepartureDelayPolicy {
    private DepartureDelayPolicy() {}

    public record Thresholds(int reminderHours, int graceMinutes, int rescheduleHours, int refundHours) {}

    public static Instant effectiveTime(Instant original, Instant revised) {
        return revised == null ? original : revised;
    }

    public static Instant effectiveTime(Departure departure) {
        return effectiveTime(departure.getPlannedDepartureAt(), departure.getRevisedDepartureAt());
    }

    public static boolean notStarted(DepartureStatus status) {
        return DepartureDecisionPolicy.NOT_STARTED.contains(status);
    }

    public static boolean needsReminder(Instant original, boolean confirmed, Instant now, Thresholds limits) {
        return !confirmed && now.isBefore(original) && !now.isBefore(original.minus(Duration.ofHours(limits.reminderHours())));
    }

    public static boolean isLate(Instant original, boolean confirmed, Instant now, Thresholds limits) {
        Instant deadline = confirmed ? original.plus(Duration.ofMinutes(limits.graceMinutes())) : original;
        return !now.isBefore(deadline);
    }

    public static boolean cancellationDue(Instant original, Instant revised, Instant now, Thresholds limits) {
        return revised == null && !now.isBefore(original.plus(Duration.ofHours(limits.refundHours())));
    }

    public static Duration delay(Instant original, Instant revised, Instant now) {
        Instant estimated = effectiveTime(original, revised);
        return Duration.between(original, estimated.isAfter(now) ? estimated : now);
    }

    public static boolean permitsReschedule(Duration delay, Thresholds limits) {
        return delay.compareTo(Duration.ofHours(limits.rescheduleHours())) >= 0;
    }

    public static boolean permitsRefund(Duration delay, Thresholds limits) {
        return delay.compareTo(Duration.ofHours(limits.refundHours())) >= 0;
    }

    public static void requireRevisable(DepartureStatus status, Instant original, Instant revised, Instant now) {
        if (!notStarted(status)) {
            throw BookingRuleException.conflict("Chuyến đã chạy hoặc đã hủy, không thể dời giờ.");
        }
        if (!revised.isAfter(original) || !revised.isAfter(now)) {
            throw new BookingRuleException("revisedDepartureAt", "Giờ mới phải sau giờ gốc và ở tương lai.");
        }
    }
}
