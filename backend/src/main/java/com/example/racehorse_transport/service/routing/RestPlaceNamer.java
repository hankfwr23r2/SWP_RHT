package com.example.racehorse_transport.service.routing;

import java.util.Comparator;
import java.util.List;

// Đặt tên chỗ nghỉ dọc đường theo trạm nghỉ gần nhất (trong 20 km) với vị trí xe lúc phải nghỉ
public final class RestPlaceNamer {

    public record Place(String name, GeoPoint point) {}

    static final double MAX_DISTANCE_KM = 20;

    private RestPlaceNamer() {}

    public static String nameFor(List<GeoPoint> legShape, double kmIntoLeg, double kmFromStart, List<Place> restStops) {
        GeoPoint here = pointAlong(legShape, kmIntoLeg);
        return restStops.stream()
                .filter(place -> GeoPoint.distanceKm(here, place.point()) <= MAX_DISTANCE_KM)
                .min(Comparator.comparingDouble(place -> GeoPoint.distanceKm(here, place.point())))
                .map(Place::name)
                .orElse("Nghỉ dọc đường (km " + Math.round(kmFromStart) + ")");
    }

    // Điểm cách đầu đường vẽ km ki-lô-mét, đo theo đường vẽ
    static GeoPoint pointAlong(List<GeoPoint> shape, double km) {
        if (shape.isEmpty()) {
            throw new IllegalArgumentException("Chặng không có đường vẽ.");
        }
        double target = Math.max(0, km);
        double walked = 0;
        for (int i = 1; i < shape.size(); i++) {
            GeoPoint a = shape.get(i - 1);
            GeoPoint b = shape.get(i);
            double segment = GeoPoint.distanceKm(a, b);
            if (segment > 0 && walked + segment >= target) {
                double f = (target - walked) / segment;
                return new GeoPoint(a.lat() + (b.lat() - a.lat()) * f, a.lng() + (b.lng() - a.lng()) * f);
            }
            walked += segment;
        }
        return shape.get(shape.size() - 1);
    }
}
