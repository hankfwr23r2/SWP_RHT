package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BookingDeadlinePolicyTest {
    @Test
    void convertsHoursBetweenTheStartAndTheDeadline() {
        Instant start = Instant.parse("2026-11-28T14:00:00Z");
        Instant deadline = Instant.parse("2026-11-28T18:00:00Z");
        assertThat(BookingDeadlinePolicy.cutoff(deadline, 4)).isEqualTo(start);
        assertThat(BookingDeadlinePolicy.cutoff(deadline.minusSeconds(1), 4)).isBefore(start);
    }

    @Test
    void switchesReminderDayAtMidnightInVietnam() {
        Instant midnight = Instant.parse("2026-11-28T17:00:00Z");
        var previous = BookingDeadlinePolicy.dayOf(midnight.minusSeconds(1));
        var next = BookingDeadlinePolicy.dayOf(midnight);

        assertThat(previous.start()).isEqualTo(Instant.parse("2026-11-27T17:00:00Z"));
        assertThat(previous.end()).isEqualTo(midnight);
        assertThat(next.start()).isEqualTo(midnight);
        assertThat(next.end()).isEqualTo(Instant.parse("2026-11-29T17:00:00Z"));
    }
}
