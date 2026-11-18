package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.util.RestPlaceNamer.Place;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// Chỗ nghỉ dọc đường lấy tên trạm nghỉ (bảng location, loại RestStop) gần vị trí đó nhất trong 20 km;
// không có trạm nào đủ gần thì ghi "Nghỉ dọc đường (km X)" tính từ điểm xuất phát.
// Đường vẽ thử: thẳng theo vĩ tuyến 10°, kinh độ 106° → 107° (khoảng 109,5 km).
class RestPlaceNamerTest {

    private static final List<GeoPointDto> STRAIGHT = List.of(new GeoPointDto(10.0, 106.0), new GeoPointDto(10.0, 107.0));

    @Test
    void findsThePointHalfwayAlongTheLeg() {
        double length = GeoPointDto.distanceKm(STRAIGHT.get(0), STRAIGHT.get(1));
        GeoPointDto middle = RestPlaceNamer.pointAlong(STRAIGHT, length / 2);

        assertThat(middle.lat()).isCloseTo(10.0, within(1e-6));
        assertThat(middle.lng()).isCloseTo(106.5, within(1e-6));
    }

    @Test
    void walksAcrossSeveralSegments() {
        List<GeoPointDto> shape = List.of(new GeoPointDto(10.0, 106.0), new GeoPointDto(10.0, 106.5), new GeoPointDto(10.0, 107.0));
        double length = GeoPointDto.distanceKm(shape.get(0), shape.get(2));

        GeoPointDto point = RestPlaceNamer.pointAlong(shape, length * 0.75);

        assertThat(point.lng()).isCloseTo(106.75, within(1e-3));
    }

    @Test
    void distanceBeyondTheLegEndsAtTheLastPoint() {
        assertThat(RestPlaceNamer.pointAlong(STRAIGHT, 500)).isEqualTo(new GeoPointDto(10.0, 107.0));
    }

    @Test
    void negativeDistanceStaysAtTheFirstPoint() {
        assertThat(RestPlaceNamer.pointAlong(STRAIGHT, -5)).isEqualTo(new GeoPointDto(10.0, 106.0));
    }

    @Test
    void namesTheNearestRestStopWithinTwentyKilometres() {
        List<Place> stops = List.of(
                new Place("Trạm xa hơn", new GeoPointDto(10.10, 106.5)),   // ~11 km
                new Place("Trạm gần nhất", new GeoPointDto(10.05, 106.5))); // ~5,6 km
        double half = GeoPointDto.distanceKm(STRAIGHT.get(0), STRAIGHT.get(1)) / 2;

        assertThat(RestPlaceNamer.nameFor(STRAIGHT, half, 154.8, stops)).isEqualTo("Trạm gần nhất");
    }

    @Test
    void fallsBackToTheKilometreMarkWhenNoRestStopIsClose() {
        List<Place> stops = List.of(new Place("Trạm quá xa", new GeoPointDto(10.5, 106.5))); // ~55 km
        double half = GeoPointDto.distanceKm(STRAIGHT.get(0), STRAIGHT.get(1)) / 2;

        assertThat(RestPlaceNamer.nameFor(STRAIGHT, half, 154.8, stops)).isEqualTo("Nghỉ dọc đường (km 155)");
    }

    @Test
    void distanceBetweenPointsIsHaversine() {
        // 1 độ kinh tuyến ở vĩ độ 10° ≈ 111,195 × cos(10°) ≈ 109,5 km
        assertThat(GeoPointDto.distanceKm(STRAIGHT.get(0), STRAIGHT.get(1))).isCloseTo(109.5, within(0.1));
        assertThat(new GeoPointDto(10.7769, 106.7009).key()).isEqualTo("10.77690,106.70090");
    }
}
