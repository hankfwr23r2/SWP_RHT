package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.LocationType;

public final class DepartureRoutePolicy {

    private DepartureRoutePolicy() {}

    public static boolean matches(Route route, BookingType type, Long pickupHubId, Long deliveryHubId, String counterpartCountryCode) {
        return switch (type) {
            case INTERPROVINCE -> route.getOrigin().getId().equals(pickupHubId)
                    && route.getDestination().getId().equals(deliveryHubId);
            case EXPORT -> route.getOrigin().getId().equals(pickupHubId)
                    && matchesGate(route.getDestination(), counterpartCountryCode);
            case IMPORT -> matchesGate(route.getOrigin(), counterpartCountryCode)
                    && route.getDestination().getId().equals(deliveryHubId);
            case LOCAL -> false;
        };
    }

    private static boolean matchesGate(Location gate, String counterpartCountryCode) {
        return gate.getLocationType() == LocationType.BORDER_GATE
                && gate.getCounterpartCountry() != null
                && gate.getCounterpartCountry().getCode().equals(counterpartCountryCode);
    }

    public static String counterpartCountryCode(BookingType type, String pickupCountryCode, String deliveryCountryCode) {
        return switch (type) {
            case EXPORT -> deliveryCountryCode;
            case IMPORT -> pickupCountryCode;
            case LOCAL, INTERPROVINCE -> null;
        };
    }

    public static Long gateId(Route route, BookingType type) {
        return switch (type) {
            case EXPORT -> route.getDestination().getId();
            case IMPORT -> route.getOrigin().getId();
            case LOCAL, INTERPROVINCE -> null;
        };
    }
}
