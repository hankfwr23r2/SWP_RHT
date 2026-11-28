package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.dto.route.RoadRouteDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveryRoutePolicyTest {
    private final TripSchedulePolicy.Rules rules = new TripSchedulePolicy.Rules(4, 30, 10, 10, 5);

    @Test
    void choosesTheShortestFeasibleOrderOnDirectedRoads() {
        RoadRouteDto[][] roads = {
                {null, road(20, 1), road(35, 1)},
                {road(20, 1), null, road(10, 1)},
                {road(35, 1), road(10, 1), null}
        };
        var route = DeliveryRoutePolicy.shortest(roads, rules, List.of(1, 1)).orElseThrow();
        assertThat(route.order()).containsExactly(1, 2);
        assertThat(route.deliveryKm()).isEqualTo(30);
        assertThat(route.individualKm()).containsExactly(20d, 35d);
    }

    @Test
    void rejectsAnOverlongRideAndAnOverlongReturn() {
        RoadRouteDto[][] roads = {
                {null, road(20, 3), road(35, 3)},
                {road(20, 1), null, road(10, 3)},
                {road(35, 1), road(10, 3), null}
        };
        assertThat(DeliveryRoutePolicy.shortest(roads, rules, List.of(1, 1))).isEmpty();
        roads[1][2] = road(10, 1);
        roads[2][0] = road(35, 7);
        assertThat(DeliveryRoutePolicy.shortest(roads, rules, List.of(1, 1))).isEmpty();
    }

    private static RoadRouteDto road(double km, int hours) {
        return new RoadRouteDto(km, hours * 3600L, List.of());
    }
}
