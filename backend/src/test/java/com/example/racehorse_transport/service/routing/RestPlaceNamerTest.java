package com.example.racehorse_transport.service.routing;

import com.example.racehorse_transport.service.routing.RestPlaceNamer.Place;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// Chỗ nghỉ dọc đường lấy tên trạm nghỉ (bảng location, loại RestStop) gần vị trí đó nhất trong 20 km;
// không có trạm nào đủ gần thì ghi "Nghỉ dọc đường (km X)" tính từ điểm xuất phát.
// Đường vẽ thử: thẳng theo vĩ tuyến 10°, kinh độ 106° → 107° (khoảng 109,5 km).
class RestPlaceNamerTest {

    private static final List<GeoPoint> STRAIGHT = List.of(new GeoPoint(10.0, 106.0), new GeoPoint(10.0, 107.0));

    @Test
    void findsThePointHalfwayAlongTheLeg() {
        double length = GeoPoint.distanceKm(STRAIGHT.get(0), STRAIGHT.get(1));
        GeoPoint middle = RestPlaceNamer.pointAlong(STRAIGHT, length / 2);

        assertThat(middle.lat()).isCloseTo(10.0, within(1e-6));
        assertThat(middle.lng()).isCloseTo(106.5, within(1e-6));
    }

    @Test
    void walksAcrossSeveralSegments() {
        List<GeoPoint> shape = List.of(new GeoPoint(10.0, 106.0), new GeoPoint(10.0, 106.5), new GeoPoint(10.0, 107.0));
        double length = GeoPoint.distanceKm(shape.get(0), shape.get(2));

        GeoPoint point = RestPlaceNamer.pointAlong(shape, length * 0.75);

        assertThat(point.lng()).isCloseTo(106.75, within(1e-3));
    }

    @Test
    void distanceBeyondTheLegEndsAtTheLastPoint() {
        assertThat(RestPlaceNamer.pointAlong(STRAIGHT, 500)).isEqualTo(new GeoPoint(10.0, 107.0));
    }

    @Test
    void negativeDistanceStaysAtTheFirstPoint() {
        assertThat(RestPlaceNamer.pointAlong(STRAIGHT, -5)).isEqualTo(new GeoPoint(10.0, 106.0));
    }

    @Test
    void namesTheNearestRestStopWithinTwentyKilometres() {
        List<Place> stops = List.of(
                new Place("Trạm xa hơn", new GeoPoint(10.10, 106.5)),   // ~11 km
                new Place("Trạm gần nhất", new GeoPoint(10.05, 106.5))); // ~5,6 km
        double half = GeoPoint.distanceKm(STRAIGHT.get(0), STRAIGHT.get(1)) / 2;

        assertThat(RestPlaceNamer.nameFor(STRAIGHT, half, 154.8, stops)).isEqualTo("Trạm gần nhất");
    }

    @Test
    void fallsBackToTheKilometreMarkWhenNoRestStopIsClose() {
        List<Place> stops = List.of(new Place("Trạm quá xa", new GeoPoint(10.5, 106.5))); // ~55 km
        double half = GeoPoint.distanceKm(STRAIGHT.get(0), STRAIGHT.get(1)) / 2;

        assertThat(RestPlaceNamer.nameFor(STRAIGHT, half, 154.8, stops)).isEqualTo("Nghỉ dọc đường (km 155)");
    }

    @Test
    void distanceBetweenPointsIsHaversine() {
        // 1 độ kinh tuyến ở vĩ độ 10° ≈ 111,195 × cos(10°) ≈ 109,5 km
        assertThat(GeoPoint.distanceKm(STRAIGHT.get(0), STRAIGHT.get(1))).isCloseTo(109.5, within(0.1));
        assertThat(new GeoPoint(10.7769, 106.7009).key()).isEqualTo("10.77690,106.70090");
    }
}
