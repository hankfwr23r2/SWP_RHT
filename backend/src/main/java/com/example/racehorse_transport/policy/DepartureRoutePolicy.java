package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.LocationType;

public final class DepartureRoutePolicy {

    private DepartureRoutePolicy() {}

    public static boolean matches(Route route, BookingType type, Long pickupHubId, Long deliveryHubId) {
        return switch (type) {
            case INTERPROVINCE -> route.getOrigin().getId().equals(pickupHubId)
                    && route.getDestination().getId().equals(deliveryHubId);
            case EXPORT -> route.getOrigin().getId().equals(pickupHubId)
                    && route.getDestination().getLocationType() == LocationType.BORDER_GATE;
            case IMPORT -> route.getOrigin().getLocationType() == LocationType.BORDER_GATE
                    && route.getDestination().getId().equals(deliveryHubId);
            case LOCAL -> false;
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
