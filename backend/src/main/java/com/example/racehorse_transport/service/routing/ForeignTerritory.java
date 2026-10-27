package com.example.racehorse_transport.service.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Lãnh thổ Campuchia và Lào (Natural Earth 1:50m, public domain) để biết tuyến nội địa có đi ra nước ngoài không.
// Ranh giới thô nên chỉ coi là "ở nước ngoài" khi điểm nằm sâu hơn ngưỡng tính từ đường biên.
@Component
public class ForeignTerritory {

    private record Area(String country, double[][] ring, double minLat, double maxLat, double minLng, double maxLng) {}

    private static final Map<String, String> NAMES = Map.of("KH", "Campuchia", "LA", "Lào");

    private final List<Area> areas = new ArrayList<>();

    public ForeignTerritory(ObjectMapper json) {
        try (InputStream in = ForeignTerritory.class.getResourceAsStream("/routing/foreign-borders.json")) {
            JsonNode root = json.readTree(in);
            for (Map.Entry<String, String> entry : NAMES.entrySet()) {
                for (JsonNode ringNode : root.path(entry.getKey())) {
                    double[][] ring = new double[ringNode.size()][2];
                    double minLat = 90, maxLat = -90, minLng = 180, maxLng = -180;
                    for (int i = 0; i < ringNode.size(); i++) {
                        ring[i][0] = ringNode.get(i).get(0).asDouble();
                        ring[i][1] = ringNode.get(i).get(1).asDouble();
                        minLng = Math.min(minLng, ring[i][0]);
                        maxLng = Math.max(maxLng, ring[i][0]);
                        minLat = Math.min(minLat, ring[i][1]);
                        maxLat = Math.max(maxLat, ring[i][1]);
                    }
                    areas.add(new Area(entry.getValue(), ring, minLat, maxLat, minLng, maxLng));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được dữ liệu ranh giới routing/foreign-borders.json", e);
        }
    }

    public String countryDeeperThan(GeoPoint point, double depthKm) {
        for (Area area : areas) {
            if (point.lat() < area.minLat() || point.lat() > area.maxLat()
                    || point.lng() < area.minLng() || point.lng() > area.maxLng()) {
                continue;
            }
            if (inside(point, area.ring()) && distanceToBoundaryKm(point, area.ring()) > depthKm) {
                return area.country();
            }
        }
        return null;
    }

    private static boolean inside(GeoPoint p, double[][] ring) {
        boolean in = false;
        for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
            double xi = ring[i][0], yi = ring[i][1], xj = ring[j][0], yj = ring[j][1];
            if ((yi > p.lat()) != (yj > p.lat()) && p.lng() < (xj - xi) * (p.lat() - yi) / (yj - yi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    // Khoảng cách ngắn nhất tới đường biên, phép chiếu phẳng cục bộ (đủ chính xác ở vài chục km)
    private static double distanceToBoundaryKm(GeoPoint p, double[][] ring) {
        double kx = 111.32 * Math.cos(Math.toRadians(p.lat()));
        double ky = 110.57;
        double px = p.lng() * kx, py = p.lat() * ky;
        double best = Double.MAX_VALUE;
        for (int i = 1; i < ring.length; i++) {
            double ax = ring[i - 1][0] * kx, ay = ring[i - 1][1] * ky;
            double bx = ring[i][0] * kx, by = ring[i][1] * ky;
            double dx = bx - ax, dy = by - ay;
            double len = dx * dx + dy * dy;
            double t = len == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len));
            best = Math.min(best, Math.hypot(px - (ax + t * dx), py - (ay + t * dy)));
        }
        return best;
    }
}
