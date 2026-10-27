package com.example.racehorse_transport.service.routing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// Trạm nghỉ được chiếu lên đường vẽ của chặng: biết trạm cách đường bao xa và nằm ở km thứ mấy từ đầu chặng.
// Đường vẽ thử: theo vĩ tuyến 10°, kinh độ 106,0 → 106,5 → 107,0 (mỗi đoạn ~54,75 km).
class RouteGeometryTest {

    private static final List<GeoPoint> SHAPE = List.of(
            new GeoPoint(10.0, 106.0), new GeoPoint(10.0, 106.5), new GeoPoint(10.0, 107.0));

    @Test
    void projectsAPointNearTheMiddleOntoTheMiddleVertex() {
        RouteGeometry.Projection p = RouteGeometry.project(SHAPE, new GeoPoint(10.05, 106.5));

        assertThat(p.index()).isEqualTo(1);
        assertThat(p.distanceKm()).isCloseTo(5.56, within(0.05));
        assertThat(p.kmAlongShape()).isCloseTo(GeoPoint.distanceKm(SHAPE.get(0), SHAPE.get(1)), within(1e-9));
    }

    @Test
    void lengthIsTheSumOfSegments() {
        assertThat(RouteGeometry.lengthKm(SHAPE)).isCloseTo(GeoPoint.distanceKm(SHAPE.get(0), SHAPE.get(2)), within(0.01));
        assertThat(RouteGeometry.lengthKm(List.of(new GeoPoint(10, 106)))).isZero();
    }
}
