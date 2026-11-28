package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.enums.BookingStatus;
import org.junit.jupiter.api.Test;

class BookingStatusPolicyTest {

    @Test
    void mainCommercialFlowIsAllowed() {
        BookingStatus[] flow = {BookingStatus.PENDING_INTAKE, BookingStatus.UNDER_REVIEW, BookingStatus.QUOTING,
                BookingStatus.AWAITING_DEPOSIT, BookingStatus.CONFIRMED, BookingStatus.IN_OPERATION,
                BookingStatus.DELIVERED, BookingStatus.COMPLETED};
        for (int i = 1; i < flow.length; i++) {
            assertThat(BookingStatusPolicy.canTransition(flow[i - 1], flow[i])).as(flow[i - 1] + " -> " + flow[i]).isTrue();
        }
    }

    @Test
    void resubmissionLoopsBackToReview() {
        assertThat(BookingStatusPolicy.canTransition(BookingStatus.UNDER_REVIEW, BookingStatus.NEEDS_RESUBMISSION)).isTrue();
        assertThat(BookingStatusPolicy.canTransition(BookingStatus.NEEDS_RESUBMISSION, BookingStatus.UNDER_REVIEW)).isTrue();
    }

    @Test
    void cancelledDepartureNeedsScheduleChangeOnlyBeforeOperation() {
        assertThat(BookingStatusPolicy.canTransition(BookingStatus.CONFIRMED, BookingStatus.SCHEDULE_CHANGE_REQUIRED)).isTrue();
        assertThat(BookingStatusPolicy.canTransition(BookingStatus.IN_OPERATION, BookingStatus.SCHEDULE_CHANGE_REQUIRED)).isFalse();
        assertThat(BookingStatusPolicy.canTransition(BookingStatus.SCHEDULE_CHANGE_REQUIRED, BookingStatus.CONFIRMED)).isTrue();
        assertThat(BookingStatusPolicy.canTransition(BookingStatus.SCHEDULE_CHANGE_REQUIRED, BookingStatus.CANCELLED)).isTrue();
    }

    @Test
    void cannotSkipStepsOrLeaveFinalStates() {
        assertThat(BookingStatusPolicy.canTransition(BookingStatus.PENDING_INTAKE, BookingStatus.CONFIRMED)).isFalse();
        assertThat(BookingStatusPolicy.canTransition(BookingStatus.IN_OPERATION, BookingStatus.CANCELLED)).isFalse();
        assertThat(BookingStatusPolicy.nextOf(BookingStatus.COMPLETED)).isEmpty();
        assertThat(BookingStatusPolicy.nextOf(BookingStatus.CANCELLED)).isEmpty();
        assertThat(BookingStatusPolicy.nextOf(BookingStatus.REJECTED)).isEmpty();
        assertThat(BookingStatusPolicy.canTransition(null, BookingStatus.UNDER_REVIEW)).isFalse();
    }

    @Test
    void onlyUnclaimedReturnFlowMayCancelAnOperatingBooking() {
        assertThat(BookingStatusPolicy.canCancelAfterUnclaimedReturns(BookingStatus.IN_OPERATION)).isTrue();
        assertThat(BookingStatusPolicy.canCancelAfterUnclaimedReturns(BookingStatus.DELIVERED)).isFalse();
        assertThat(BookingStatusPolicy.canTransition(BookingStatus.IN_OPERATION, BookingStatus.CANCELLED)).isFalse();
    }

    @Test
    void everyStatusHasAnEntry() {
        for (BookingStatus status : BookingStatus.values()) {
            assertThat(BookingStatusPolicy.nextOf(status)).as(status.name()).isNotNull();
        }
    }
}
