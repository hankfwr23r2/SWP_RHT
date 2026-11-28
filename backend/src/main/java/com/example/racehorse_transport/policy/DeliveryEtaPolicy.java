package com.example.racehorse_transport.policy;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import com.example.racehorse_transport.policy.CustomerNoticePolicy.HourWindow;

public final class DeliveryEtaPolicy {
    public static final String TOLERANCE = "DELIVERY_TOLERANCE_MINUTES";
    public static final String NOTICE_THRESHOLD = "SCHEDULE_CHANGE_NOTICE_MINUTES";

    private DeliveryEtaPolicy() {}

    public static Instant arrival(Instant departure, Instant plannedDeparture, Instant plannedArrival) {
        return departure.plus(Duration.between(plannedDeparture, plannedArrival));
    }

    public static boolean changed(Instant previous, Instant expected, int minutes) {
        return Duration.between(previous, expected).abs().compareTo(Duration.ofMinutes(minutes)) > 0;
    }

    public static boolean held(Instant actualArrival, Instant plannedArrival, Instant plannedDeparture,
                               Instant now, int minutes) {
        return now.isAfter(arrival(actualArrival, plannedArrival, plannedDeparture).plus(Duration.ofMinutes(minutes)));
    }

    public static Instant reference(Instant told, Instant planned) {
        return told == null ? planned : told;
    }

    public static boolean companyFault(Instant reference, Instant actual, int tolerance) {
        return reference != null && actual != null && actual.isAfter(reference.plus(Duration.ofMinutes(tolerance)));
    }

    public static Instant waitStart(Instant now, Instant reference, int tolerance) {
        if (reference == null) return now;
        Instant start = reference.minus(Duration.ofMinutes(tolerance));
        return start.isAfter(now) ? start : now;
    }

    public static HourWindow window(Instant reference, int tolerance) {
        var offset = Duration.ofMinutes(tolerance);
        return new HourWindow(reference.minus(offset), reference.plus(offset));
    }

    public static boolean afterDeliveryEnd(Instant planned, Instant expected, int endHour, ZoneId zone) {
        var plannedDay = planned.atZone(zone).toLocalDate();
        var expectedDay = expected.atZone(zone).toLocalDate();
        var deliveryDay = expectedDay.isBefore(plannedDay) ? expectedDay : plannedDay;
        var deadline = deliveryDay.atStartOfDay(zone).plusHours(endHour);
        return expected.isAfter(deadline.toInstant());
    }
}
