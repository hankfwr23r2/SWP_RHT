package com.example.racehorse_transport.service.routing;

import java.util.Locale;

public record GeoPoint(double lat, double lng) {

    private static final double EARTH_RADIUS_KM = 6371.0;

    public static double distanceKm(GeoPoint a, GeoPoint b) {
        double rad = Math.PI / 180;
        double dLat = (b.lat - a.lat) * rad;
        double dLng = (b.lng - a.lng) * rad;
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }

    // Khóa lưu đệm: làm tròn 5 chữ số (~1 m) để cùng một địa điểm luôn ra cùng khóa
    public String key() {
        return String.format(Locale.ROOT, "%.5f,%.5f", lat, lng);
    }
}
