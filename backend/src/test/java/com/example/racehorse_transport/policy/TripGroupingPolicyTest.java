package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.policy.TripGroupingPolicy.Candidate;
import com.example.racehorse_transport.policy.TripGroupingPolicy.Group;
import com.example.racehorse_transport.policy.TripGroupingPolicy.Point;
import java.util.List;
import org.junit.jupiter.api.Test;

class TripGroupingPolicyTest {

    // Hub ở gốc tọa độ; 0,1 độ vĩ ≈ 11 km đường chim bay
    private static final Point HUB = new Point(10.0, 106.0);

    private static Candidate at(long id, double dLat, double dLng, int seats) {
        return new Candidate(id, new Point(10.0 + dLat, 106.0 + dLng), seats);
    }

    @Test
    void bookingsAlongTheSameDirectionShareOneTripFarthestFirst() {
        List<Group> groups = TripGroupingPolicy.group(HUB, List.of(at(1, 0.3, 0, 1), at(2, 0.6, 0.02, 1)), 9, 4, 60);
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().order()).extracting(Candidate::bookingId).containsExactly(2L, 1L);
    }

    @Test
    void oppositeDirectionsAreNotGroupedBecauseOfTheDetour() {
        // Một đơn phía bắc, một đơn phía nam: ghép thì đơn đầu đi vòng gấp nhiều lần
        List<Group> groups = TripGroupingPolicy.group(HUB, List.of(at(1, 0.3, 0, 1), at(2, -0.3, 0, 1)), 9, 4, 60);
        assertThat(groups).hasSize(2);
    }

    @Test
    void seatsAndHoursLimitTheGroup() {
        // Xe 2 chỗ: khoang rộng của đơn 2 cộng con của đơn 1 vượt chỗ
        assertThat(TripGroupingPolicy.group(HUB, List.of(at(1, 0.3, 0, 1), at(2, 0.6, 0, 2)), 2, 4, 60)).hasSize(2);
        // Ghép thì con đón đầu ở trên xe gần 1 giờ, quá giới hạn 0,9 giờ
        assertThat(TripGroupingPolicy.group(HUB, List.of(at(1, 0.2, 0, 1), at(2, 0.4, 0.05, 1)), 9, 0.9, 60)).hasSize(2);
        assertThat(TripGroupingPolicy.group(HUB, List.of(at(1, 0.2, 0, 1), at(2, 0.4, 0.05, 1)), 9, 4, 60)).hasSize(1);
    }

    @Test
    void deliveryRadiusAndPrivateBookingsRestrictCompatibleRoutes() {
        var first = at(1, 0.3, 0, 1);
        var far = at(2, 0.9, 0, 1);
        var privateBooking = new Candidate(3L, first.point(), 1, true);
        assertThat(TripGroupingPolicy.group(HUB, List.of(first, far), 9, 4, 60)).hasSize(1);
        assertThat(TripGroupingPolicy.group(HUB, List.of(first, far, privateBooking), 9, 4, 60, 60)).hasSize(3);
    }

    @Test
    void reportsDistanceAndHoursOfTheRoute() {
        Group alone = TripGroupingPolicy.group(HUB, List.of(at(1, 0.9, 0, 1)), 9, 4, 50).getFirst();
        assertThat(alone.routeKm()).isBetween(125.0, 135.0);
        assertThat(alone.hours()).isEqualTo(alone.routeKm() / 50);
    }
}
