package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DepartureDecisionPolicyTest {

    private static final Instant PLANNED = Instant.parse("2026-12-21T23:00:00Z");
    private static final Instant DECISION = PLANNED.minus(Duration.ofHours(48));

    @Test
    void confirmableOnlyBetweenDecisionTimeAndDeparture() {
        assertThat(DepartureDecisionPolicy.decisionAt(PLANNED, 48)).isEqualTo(DECISION);
        assertThat(reason(DepartureStatus.CLOSED, false, DECISION.minusSeconds(1))).contains("mốc quyết định");
        assertThat(reason(DepartureStatus.CLOSED, false, DECISION)).isEmpty();
        assertThat(reason(DepartureStatus.CLOSED, false, PLANNED.minusSeconds(1))).isEmpty();
        assertThat(reason(DepartureStatus.CLOSED, false, PLANNED)).contains("qua giờ xuất phát");
    }

    @Test
    void rejectsStartedOrCancelledOrAlreadyConfirmed() {
        assertThat(reason(DepartureStatus.IN_PROGRESS, false, DECISION)).contains("đã chạy");
        assertThat(reason(DepartureStatus.CANCELLED, false, DECISION)).contains("đã hủy");
        assertThat(reason(DepartureStatus.CLOSED, true, DECISION)).contains("đã được xác nhận");
    }

    @Test
    void warnsStrictlyBelowMinimumFill() {
        assertThat(DepartureDecisionPolicy.belowMinimum(2, 9, 30)).isTrue();
        assertThat(DepartureDecisionPolicy.belowMinimum(3, 9, 30)).isFalse();
        assertThat(DepartureDecisionPolicy.belowMinimum(3, 10, 30)).isFalse();
    }

    @Test
    void onlyReceivedHorsesBlockCancellationAndQuotedBookingsKeepSeats() {
        assertThat(DepartureDecisionPolicy.blocksCancellation(BookingStatus.IN_OPERATION)).isTrue();
        assertThat(DepartureDecisionPolicy.blocksCancellation(BookingStatus.CONFIRMED)).isFalse();
        assertThat(DepartureDecisionPolicy.RELEASABLE)
                .doesNotContain(BookingStatus.AWAITING_DEPOSIT, BookingStatus.CONFIRMED, BookingStatus.IN_OPERATION);
        assertThat(DepartureDecisionPolicy.COMMITTED)
                .containsExactlyInAnyOrder(BookingStatus.CONFIRMED, BookingStatus.IN_OPERATION);
    }

    private static String reason(DepartureStatus status, boolean confirmed, Instant now) {
        return DepartureDecisionPolicy.reasonNotConfirmable(status, PLANNED, 48, confirmed, now).orElse("");
    }
}
