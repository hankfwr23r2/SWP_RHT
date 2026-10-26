package com.example.racehorse_transport.service.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

// Gọi dịch vụ thật để biết mẫu đã lưu còn đúng định dạng. Không chạy trên CI:
// Valhalla chỉ chạy khi ROUTING_LIVE_TEST=1 (máy chủ công khai, giới hạn ~1 lần/giây); Vietmap chỉ chạy khi có VIETMAP_API_KEY.
class RoutingLiveTest {

    private static final GeoPoint HCM = new GeoPoint(10.7769, 106.7009);
    private static final GeoPoint MOC_BAI = new GeoPoint(11.0796, 106.1572);
    private static final GeoPoint PHNOM_PENH = new GeoPoint(11.5564, 104.9282);

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @EnabledIfEnvironmentVariable(named = "ROUTING_LIVE_TEST", matches = "1")
    void valhallaReturnsATruckRouteAcrossTheBorder() {
        RoadRoute route = new ValhallaClient("https://valhalla1.openstreetmap.de", json)
                .truckRoute(List.of(HCM, MOC_BAI, PHNOM_PENH), 8, 80, LocalDateTime.now().plusDays(1).withHour(5).withMinute(0));

        assertThat(route.legs()).hasSize(2);
        assertThat(route.legs().get(0).km()).isBetween(60.0, 100.0);
        assertThat(route.legs().get(1).km()).isBetween(140.0, 200.0);
        assertThat(route.legs().get(1).driveSeconds()).isPositive();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "VIETMAP_API_KEY", matches = ".+")
    void vietmapReturnsATruckTollInsideVietnam() {
        OptionalLong toll = new VietmapTollClient("https://maps.vietmap.vn/api", System.getenv("VIETMAP_API_KEY"), json)
                .truckToll(HCM, MOC_BAI, 8000, Instant.now().plusSeconds(86_400));

        assertThat(toll).isPresent();
        assertThat(toll.getAsLong()).isNotNegative();
    }
}
