package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.dto.route.RoadRouteDto;
import java.time.LocalDateTime;
import java.util.List;

public interface RoadRoutingService {

    public record Waypoint(GeoPointDto point, boolean through) {
        public static Waypoint stop(GeoPointDto point) {
            return new Waypoint(point, false);
        }
    }

    RoadRouteDto truckRoute(List<GeoPointDto> points, double weightTons, int topSpeedKmh, LocalDateTime departLocal);

    RoadRouteDto truckRouteVia(List<Waypoint> waypoints, double weightTons, int topSpeedKmh, LocalDateTime departLocal);
}
