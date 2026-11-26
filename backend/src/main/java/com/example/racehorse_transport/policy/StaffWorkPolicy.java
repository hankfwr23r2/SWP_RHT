package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import java.util.Map;
import static com.example.racehorse_transport.entity.enums.BookingStatus.*;

public final class StaffWorkPolicy {
    private StaffWorkPolicy() {}

    public static Map<String, Long> orderFilters(Map<BookingStatus, Long> counts, long documents) {
        return Map.of(
                "intake", total(counts, PENDING_INTAKE),
                "quoting", total(counts, QUOTING),
                "documents", documents,
                "settle", total(counts, DELIVERED),
                "review", total(counts, UNDER_REVIEW, NEEDS_RESUBMISSION),
                "deposit", total(counts, AWAITING_DEPOSIT),
                "confirmed", total(counts, CONFIRMED, SCHEDULE_CHANGE_REQUIRED),
                "moving", total(counts, IN_OPERATION),
                "closed", total(counts, COMPLETED, CANCELLED, REJECTED, QUOTE_EXPIRED));
    }

    public static long orderWork(Map<String, Long> filters) {
        return filters.get("intake") + filters.get("quoting") + filters.get("documents") + filters.get("settle");
    }

    private static long total(Map<BookingStatus, Long> counts, BookingStatus... statuses) {
        long total = 0;
        for (var status : statuses) {
            total += counts.getOrDefault(status, 0L);
        }
        return total;
    }
}
