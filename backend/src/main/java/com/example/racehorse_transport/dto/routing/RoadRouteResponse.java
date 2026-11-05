package com.example.racehorse_transport.dto.routing;

import java.util.List;

// routes[0]: đường chính; các phần tử sau: đường tránh. path: [vĩ độ, kinh độ]
public record RoadRouteResponse(List<Route> routes, List<String> warnings) {

    public record Route(double km, double driveHours, List<double[]> path) {}
}
