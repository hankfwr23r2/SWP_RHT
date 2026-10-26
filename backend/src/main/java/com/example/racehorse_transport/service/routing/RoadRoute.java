package com.example.racehorse_transport.service.routing;

import java.util.List;

// Tuyến xe tải: tổng km, tổng giây lái, từng chặng giữa hai điểm dừng liên tiếp kèm đường vẽ
public record RoadRoute(double km, long driveSeconds, List<Leg> legs) {

    public record Leg(double km, long driveSeconds, List<GeoPoint> shape) {}
}
