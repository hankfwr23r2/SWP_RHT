package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Country;
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
        assertThat(DepartureRoutePolicy.matches(route, BookingType.INTERPROVINCE, 1L, 2L, null)).isTrue();
        assertThat(DepartureRoutePolicy.matches(route, BookingType.INTERPROVINCE, 2L, 1L, null)).isFalse();
        assertThat(DepartureRoutePolicy.matches(route, BookingType.EXPORT, 1L, null, "KH")).isFalse();

        Location gate = location(3L, LocationType.BORDER_GATE);
        gate.setCode("GATE-MOCBAI");
        route.setDestination(gate);
        assertThat(DepartureRoutePolicy.matches(route, BookingType.EXPORT, 1L, null, "KH")).isFalse();
        Country cambodia = new Country();
        cambodia.setCode("KH");
        gate.setCounterpartCountry(cambodia);
        assertThat(DepartureRoutePolicy.matches(route, BookingType.EXPORT, 1L, null, "LA")).isFalse();
        assertThat(DepartureRoutePolicy.matches(route, BookingType.EXPORT, 1L, null, "KH")).isTrue();
        assertThat(DepartureRoutePolicy.gateId(route, BookingType.EXPORT)).isEqualTo(3L);
        route.setOrigin(gate);
        assertThat(DepartureRoutePolicy.matches(route, BookingType.IMPORT, null, 1L, "LA")).isFalse();
        route.setDestination(location(1L, LocationType.HUB));
        assertThat(DepartureRoutePolicy.matches(route, BookingType.IMPORT, null, 1L, "KH")).isTrue();
        assertThat(DepartureRoutePolicy.gateId(route, BookingType.IMPORT)).isEqualTo(3L);
    }

    private static Location location(Long id, LocationType type) {
        Location location = new Location();
        location.setId(id);
        location.setLocationType(type);
        return location;
    }
}
