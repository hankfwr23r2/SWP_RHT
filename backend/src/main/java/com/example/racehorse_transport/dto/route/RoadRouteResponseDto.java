package com.example.racehorse_transport.dto.route;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

// routes[0]: đường chính; các phần tử sau: đường tránh. path: [vĩ độ, kinh độ]
@Schema(name = "RoadRouteResponse")
public record RoadRouteResponseDto(List<Route> routes, List<String> warnings) {

    public record Route(double km, double driveHours, List<double[]> path) {}
}
