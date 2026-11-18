package com.example.racehorse_transport.dto.route;

import java.util.List;

// Tuyến xe tải: tổng km, tổng giây lái, từng chặng giữa hai điểm dừng liên tiếp kèm đường vẽ
public record RoadRouteDto(double km, long driveSeconds, List<Leg> legs) {

    public record Leg(double km, long driveSeconds, List<GeoPointDto> shape) {}
}
