package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.LocationType;
import java.util.List;
import org.junit.jupiter.api.Test;

class DepartureRoutePolicyTest {

    @Test
    void checksDirectionAndBothEndpoints() {
        Route route = new Route();
        route.setOrigin(location(1L, LocationType.HUB));
        route.setDestination(location(2L, LocationType.HUB));
        for (BookingType type : List.of(BookingType.INTERPROVINCE, BookingType.EXPORT, BookingType.IMPORT)) {
            assertThat(DepartureRoutePolicy.matches(route, type, 1L, 2L)).as("%s", type).isTrue();
            assertThat(DepartureRoutePolicy.matches(route, type, 2L, 2L)).as("%s pickup", type).isFalse();
            assertThat(DepartureRoutePolicy.matches(route, type, 1L, 1L)).as("%s delivery", type).isFalse();
        }
        assertThat(DepartureRoutePolicy.matches(route, BookingType.LOCAL, 1L, 2L)).isFalse();
    }

    @Test
    void onlyInternationalBookingsUseTheFirstBorderStop() {
        List<Long> borderIds = List.of(3L, 4L);
        for (BookingType type : List.of(BookingType.EXPORT, BookingType.IMPORT)) {
            assertThat(DepartureRoutePolicy.gateId(type, borderIds)).isEqualTo(3L);
            assertThat(DepartureRoutePolicy.gateId(type, List.of())).isNull();
            assertThat(DepartureRoutePolicy.gateId(type, null)).isNull();
        }
        assertThat(DepartureRoutePolicy.gateId(BookingType.LOCAL, borderIds)).isNull();
        assertThat(DepartureRoutePolicy.gateId(BookingType.INTERPROVINCE, borderIds)).isNull();
    }

    private static Location location(Long id, LocationType type) {
        Location location = new Location();
        location.setId(id);
        location.setLocationType(type);
        return location;
    }
}
