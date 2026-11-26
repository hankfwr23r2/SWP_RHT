package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.policy.LinehaulAllocationPolicy.BookingLoad;
import com.example.racehorse_transport.policy.LinehaulAllocationPolicy.Slot;
import com.example.racehorse_transport.policy.TripGroupingPolicy.Point;
import java.util.List;
import org.junit.jupiter.api.Test;

class LinehaulAllocationPolicyTest {
    private static final Point CITY = new Point(21.03, 105.85);
    private static final Point SUBURB = new Point(21.04, 105.89);
    private static final Point FAR = new Point(20.85, 106.68);

    @Test
    void keepsNearbyBookingsTogetherAndUsesSmallestTruckForEachCluster() {
        var plan = LinehaulAllocationPolicy.suggest(List.of(shared(1, 5, CITY), shared(2, 3, SUBURB), shared(3, 3, FAR)),
                List.of(new Slot(10, 1, null, 9), new Slot(11, 1, null, 6), new Slot(12, 1, null, 9)), 11, 60);
        assertThat(plan.missingSeats()).isZero();
        assertThat(plan.allocations()).hasSize(2);
        var small = plan.allocations().getFirst();
        var large = plan.allocations().getLast();
        assertThat(small.slot().capacity()).isEqualTo(6);
        assertThat(small.loads()).extracting(load -> load.booking().bookingId()).containsExactly(3L);
        assertThat(large.slot().allotmentId()).isEqualTo(10);
        assertThat(large.loads()).extracting(load -> load.booking().bookingId()).containsExactly(1L, 2L);
        assertThat(large.loads()).extracting(load -> load.cluster()).containsOnly(1);
    }

    @Test
    void usesOnlyThePrivateBookingReservation() {
        var plan = LinehaulAllocationPolicy.suggest(List.of(new BookingLoad(7, true, 2, List.of(CITY)), shared(8, 2, CITY)),
                List.of(new Slot(10, 1, 7L, 6), new Slot(11, 1, null, 2)), 2, 60);
        assertThat(plan.missingSeats()).isZero();
        assertThat(plan.allocations().getLast().loads()).extracting(load -> load.booking().bookingId()).containsExactly(7L);
        assertThat(plan.allocations().getFirst().loads()).extracting(load -> load.booking().bookingId()).containsExactly(8L);
    }

    @Test
    void repacksWhenBestFitWouldLeaveUnusableGaps() {
        var plan = LinehaulAllocationPolicy.suggest(
                List.of(shared(1, 5, CITY), shared(2, 4, CITY), shared(3, 3, CITY), shared(4, 3, CITY)),
                List.of(new Slot(10, 1, null, 9), new Slot(11, 1, null, 6)), 15, 60);
        assertThat(plan.missingSeats()).isZero();
        assertThat(plan.allocations()).extracting(LinehaulAllocationPolicy.Allocation::seats).containsExactly(6, 9);
    }

    @Test
    void warnsWhenCapacityCannotBeUsedWithoutSplittingABooking() {
        var plan = LinehaulAllocationPolicy.suggest(List.of(shared(1, 5, CITY)),
                List.of(new Slot(10, 1, null, 4), new Slot(10, 2, null, 4)), 5, 60);
        assertThat(plan.missingSeats()).isEqualTo(5);
        assertThat(plan.allocations()).allSatisfy(allocation -> assertThat(allocation.loads()).isEmpty());
    }

    @Test
    void warnsWhenTheOnlyFreeTruckBelongsToAnotherPrivateBooking() {
        var plan = LinehaulAllocationPolicy.suggest(List.of(new BookingLoad(7, true, 2, List.of(CITY))),
                List.of(new Slot(10, 1, 9L, 6), new Slot(11, 1, null, 2)), 0, 60);
        assertThat(plan.missingSeats()).isEqualTo(2);
        assertThat(plan.allocations()).isEmpty();
    }

    private static BookingLoad shared(long id, int seats, Point point) {
        return new BookingLoad(id, false, seats, List.of(point));
    }
}
