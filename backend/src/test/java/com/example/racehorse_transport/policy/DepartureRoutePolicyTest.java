package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.LocationType;
import org.junit.jupiter.api.Test;

class DepartureRoutePolicyTest {

    @Test
    void checksDirectionAndBothEndpoints() {
        Route route = new Route();
        route.setOrigin(location(1L, LocationType.HUB));
        route.setDestination(location(2L, LocationType.HUB));
        assertThat(DepartureRoutePolicy.matches(route, BookingType.INTERPROVINCE, 1L, 2L)).isTrue();
        assertThat(DepartureRoutePolicy.matches(route, BookingType.INTERPROVINCE, 2L, 1L)).isFalse();
        assertThat(DepartureRoutePolicy.matches(route, BookingType.EXPORT, 1L, null)).isFalse();

        route.setDestination(location(3L, LocationType.BORDER_GATE));
        assertThat(DepartureRoutePolicy.matches(route, BookingType.EXPORT, 1L, null)).isTrue();
        assertThat(DepartureRoutePolicy.gateId(route, BookingType.EXPORT)).isEqualTo(3L);
        route.setOrigin(location(3L, LocationType.BORDER_GATE));
        route.setDestination(location(1L, LocationType.HUB));
        assertThat(DepartureRoutePolicy.matches(route, BookingType.IMPORT, null, 1L)).isTrue();
        assertThat(DepartureRoutePolicy.gateId(route, BookingType.IMPORT)).isEqualTo(3L);
    }

    private static Location location(Long id, LocationType type) {
        Location location = new Location();
        location.setId(id);
        location.setLocationType(type);
        return location;
    }
}
