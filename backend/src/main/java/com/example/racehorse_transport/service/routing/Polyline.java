package com.example.racehorse_transport.service.routing;

import java.util.ArrayList;
import java.util.List;

// Giải mã polyline (định dạng của Google, Valhalla dùng độ chính xác 6)
public final class Polyline {

    private Polyline() {}

    public static List<GeoPoint> decode(String encoded, int precision) {
        double factor = Math.pow(10, precision);
        List<GeoPoint> points = new ArrayList<>();
        int index = 0;
        int lat = 0;
        int lng = 0;
        while (index < encoded.length()) {
            int shift = 0;
            int value = 0;
            int b;
            do {
                b = encoded.charAt(index++) - 63;
                value |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lat += (value & 1) != 0 ? ~(value >> 1) : (value >> 1);
            shift = 0;
            value = 0;
            do {
                b = encoded.charAt(index++) - 63;
                value |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lng += (value & 1) != 0 ? ~(value >> 1) : (value >> 1);
            points.add(new GeoPoint(lat / factor, lng / factor));
        }
        return points;
    }
}
