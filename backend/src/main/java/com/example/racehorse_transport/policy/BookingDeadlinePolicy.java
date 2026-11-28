package com.example.racehorse_transport.policy;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public final class BookingDeadlinePolicy {
    public static final String BUSINESS_ZONE = "Asia/Ho_Chi_Minh";

    private BookingDeadlinePolicy() {}

    public static Instant cutoff(Instant now, int hours) {
        return now.minus(Duration.ofHours(hours));
    }

    public static Day dayOf(Instant now) {
        var zone = ZoneId.of(BUSINESS_ZONE);
        var date = now.atZone(zone).toLocalDate();
        return new Day(date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant());
    }

    public record Day(Instant start, Instant end) {}
}
