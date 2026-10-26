package com.example.racehorse_transport.service.routing;

import java.util.List;

// Chiếu một điểm lên đường vẽ của chặng (theo đỉnh gần nhất của đường vẽ; đường vẽ Valhalla dày nên đủ chính xác)
public final class RouteGeometry {

    public record Projection(int index, double distanceKm, double kmAlongShape) {}

    private RouteGeometry() {}

    public static Projection project(List<GeoPoint> shape, GeoPoint point) {
        if (shape.isEmpty()) {
            throw new IllegalArgumentException("Chặng không có đường vẽ.");
        }
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        double walked = 0;
        double bestAlong = 0;
        for (int i = 0; i < shape.size(); i++) {
            if (i > 0) {
                walked += GeoPoint.distanceKm(shape.get(i - 1), shape.get(i));
            }
            double d = GeoPoint.distanceKm(shape.get(i), point);
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
                bestAlong = walked;
            }
        }
        return new Projection(best, bestDistance, bestAlong);
    }

    public static double lengthKm(List<GeoPoint> shape) {
        double total = 0;
        for (int i = 1; i < shape.size(); i++) {
            total += GeoPoint.distanceKm(shape.get(i - 1), shape.get(i));
        }
        return total;
    }
}
