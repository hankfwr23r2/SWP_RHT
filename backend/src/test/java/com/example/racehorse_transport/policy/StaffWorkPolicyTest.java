package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.racehorse_transport.entity.enums.BookingStatus.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StaffWorkPolicyTest {
    @Test
    void orderMenuCountsOnlyActionableTabs() {
        var tabs = StaffWorkPolicy.orderFilters(Map.of(PENDING_INTAKE, 3L, QUOTING, 4L, DELIVERED, 6L,
                UNDER_REVIEW, 10L, NEEDS_RESUBMISSION, 20L, QUOTE_EXPIRED, 2L, REJECTED, 3L), 5L);

        assertThat(tabs).containsEntry("review", 30L).containsEntry("closed", 5L).containsEntry("moving", 0L);
        assertThat(StaffWorkPolicy.orderWork(tabs)).isEqualTo(18);
    }
}
