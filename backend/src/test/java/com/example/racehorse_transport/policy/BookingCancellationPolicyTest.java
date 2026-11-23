package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.policy.BookingCancellationPolicy.Breakdown;
import com.example.racehorse_transport.policy.BookingCancellationPolicy.Outcome;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BookingCancellationPolicyTest {

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"PENDING_INTAKE", "UNDER_REVIEW", "NEEDS_RESUBMISSION", "QUOTING",
            "AWAITING_DEPOSIT", "QUOTE_EXPIRED"})
    void withdrawingBeforeDepositIsFree(BookingStatus status) {
        assertThat(BookingCancellationPolicy.byCustomer(status, false, false)).isEqualTo(Outcome.FREE);
        assertThat(BookingCancellationPolicy.byCustomer(status, true, false)).isEqualTo(Outcome.NOT_ALLOWED);
        assertThat(BookingCancellationPolicy.companyCanCancel(status)).isTrue();
    }

    @Test
    void confirmedBookingForfeitsDepositUntilPaidInFull() {
        assertThat(BookingCancellationPolicy.byCustomer(BookingStatus.CONFIRMED, true, false)).isEqualTo(Outcome.FORFEIT_DEPOSIT);
        assertThat(BookingCancellationPolicy.byCustomer(BookingStatus.CONFIRMED, true, true)).isEqualTo(Outcome.NOT_ALLOWED);
        assertThat(BookingCancellationPolicy.companyCanCancel(BookingStatus.CONFIRMED)).isTrue();
    }

    @Test
    void companyCancelledDepartureIsFreeEvenWhenPaidInFull() {
        assertThat(BookingCancellationPolicy.byCustomer(BookingStatus.SCHEDULE_CHANGE_REQUIRED, true, true)).isEqualTo(Outcome.FREE);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"IN_OPERATION", "DELIVERED", "COMPLETED", "CANCELLED", "REJECTED"})
    void nobodyCancelsAfterPickupOrWhenClosed(BookingStatus status) {
        assertThat(BookingCancellationPolicy.byCustomer(status, true, true)).isEqualTo(Outcome.NOT_ALLOWED);
        assertThat(BookingCancellationPolicy.companyCanCancel(status)).isFalse();
    }

    @Test
    void splitsMoney() {
        assertSplit(BookingCancellationPolicy.refundAll(new BigDecimal("3700000")), "3700000", "0", "0");
        assertSplit(BookingCancellationPolicy.forfeit(new BigDecimal("3700000"), new BigDecimal("1000000")), "0", "1000000", "2700000");
        assertSplit(BookingCancellationPolicy.forfeit(new BigDecimal("500000"), new BigDecimal("1000000")), "0", "500000", "0");
    }

    private static void assertSplit(Breakdown actual, String refundable, String reviewable, String forfeited) {
        assertThat(actual.refundable()).isEqualByComparingTo(refundable);
        assertThat(actual.reviewable()).isEqualByComparingTo(reviewable);
        assertThat(actual.forfeited()).isEqualByComparingTo(forfeited);
    }
}
