package com.example.racehorse_transport.service.routing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// Valhalla trả đường vẽ dạng polyline mã hóa (độ chính xác 6); giải mã sai thì bản đồ và tên chỗ nghỉ đều sai.
class PolylineTest {

    @Test
    void decodesTheReferenceExampleOfTheFormat() {
        // Ví dụ chuẩn của định dạng polyline (độ chính xác 5)
        List<GeoPoint> points = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5);

        assertThat(points).hasSize(3);
        assertThat(points.get(0).lat()).isCloseTo(38.5, within(1e-9));
        assertThat(points.get(0).lng()).isCloseTo(-120.2, within(1e-9));
        assertThat(points.get(1).lat()).isCloseTo(40.7, within(1e-9));
        assertThat(points.get(1).lng()).isCloseTo(-120.95, within(1e-9));
        assertThat(points.get(2).lat()).isCloseTo(43.252, within(1e-9));
        assertThat(points.get(2).lng()).isCloseTo(-126.453, within(1e-9));
    }

    @Test
    void emptyStringIsNoPoints() {
        assertThat(Polyline.decode("", 6)).isEmpty();
    }
}
