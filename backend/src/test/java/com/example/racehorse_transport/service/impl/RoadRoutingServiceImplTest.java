package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.dto.route.RoadRouteDto;
import com.example.racehorse_transport.exception.NoTruckRouteException;
import com.example.racehorse_transport.exception.RoutingUnavailableException;
import com.example.racehorse_transport.routing.RoutingStubServer;
import com.example.racehorse_transport.service.RoadRoutingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

// Đường đi thật của xe tải lấy từ Valhalla: km và thời gian lái từng chặng như Valhalla trả, không nhân hệ số.
// Máy chủ giả trả mẫu thật HCM → cửa khẩu Mộc Bài → Phnom Penh (xe tải 15 tấn, gọi ngày 26/10/2026).
class RoadRoutingServiceImplTest {

    private static final GeoPointDto HCM = new GeoPointDto(10.7769, 106.7009);
    private static final GeoPointDto MOC_BAI = new GeoPointDto(11.0796, 106.1572);
    private static final GeoPointDto PHNOM_PENH = new GeoPointDto(11.5564, 104.9282);

    private final ObjectMapper json = new ObjectMapper();
    private RoutingStubServer stub;
    private RoadRoutingService client;

    @BeforeEach
    void start() {
        stub = new RoutingStubServer();
        client = new RoadRoutingServiceImpl(stub.url(), json);
    }

    @AfterEach
    void stop() {
        stub.close();
    }

    @Test
    void readsDistanceAndDrivingTimeOfEveryLeg() {
        stub.reply("/route", 200, "valhalla-truck-hcm-mocbai-phnompenh.json");

        RoadRouteDto route = client.truckRoute(List.of(HCM, MOC_BAI, PHNOM_PENH), 15, 80, LocalDateTime.parse("2026-10-28T05:00"));

        assertThat(route.km()).isCloseTo(238.729, within(1e-6));
        assertThat(route.driveSeconds()).isEqualTo(20501);
        assertThat(route.legs()).hasSize(2);
        assertThat(route.legs().get(0).km()).isCloseTo(74.761, within(1e-6));
        assertThat(route.legs().get(0).driveSeconds()).isEqualTo(8029);
        assertThat(route.legs().get(1).km()).isCloseTo(163.967, within(1e-6));
        assertThat(route.legs().get(1).driveSeconds()).isEqualTo(12472);
        // Đường vẽ bắt đầu ở điểm đón, chặng 1 kết thúc ở cửa khẩu, chặng 2 kết thúc ở Phnom Penh
        assertThat(GeoPointDto.distanceKm(route.legs().get(0).shape().get(0), HCM)).isLessThan(1);
        assertThat(GeoPointDto.distanceKm(route.legs().get(0).shape().getLast(), MOC_BAI)).isLessThan(2);
        assertThat(GeoPointDto.distanceKm(route.legs().get(1).shape().getLast(), PHNOM_PENH)).isLessThan(2);
    }

    @Test
    void asksForATruckOfTheGivenWeightTopSpeedAndDepartureTime() throws Exception {
        stub.reply("/route", 200, "valhalla-truck-hcm-mocbai-phnompenh.json");

        client.truckRoute(List.of(HCM, MOC_BAI, PHNOM_PENH), 8, 70, LocalDateTime.parse("2026-11-02T05:00:41"));

        JsonNode sent = json.readTree(stub.requests("/route").getFirst().body());
        assertThat(sent.path("costing").asText()).isEqualTo("truck");
        assertThat(sent.path("costing_options").path("truck").path("weight").asDouble()).isEqualTo(8.0);
        assertThat(sent.path("costing_options").path("truck").path("top_speed").asInt()).isEqualTo(70);
        assertThat(sent.path("units").asText()).isEqualTo("kilometers");
        assertThat(sent.path("date_time").path("type").asInt()).isEqualTo(1);
        assertThat(sent.path("date_time").path("value").asText()).isEqualTo("2026-11-02T05:00");
        assertThat(sent.path("locations")).hasSize(3);
        assertThat(sent.path("locations").get(1).path("lat").asDouble()).isEqualTo(11.0796);
        assertThat(sent.path("locations").get(1).path("lon").asDouble()).isEqualTo(106.1572);
        assertThat(sent.path("locations").get(1).path("type").asText()).isEqualTo("break");
    }

    @Test
    void noTruckPathIsReportedAsSuch() {
        stub.reply("/route", 400, "valhalla-truck-no-route-sea.json");

        assertThatThrownBy(() -> client.truckRoute(List.of(HCM, new GeoPointDto(8.5, 112.0)), 15, 80, LocalDateTime.parse("2026-11-02T05:00")))
                .isInstanceOf(NoTruckRouteException.class)
                .hasMessageContaining("Không tìm được đường cho xe tải");
    }

    @Test
    void otherValhallaErrorsMeanTheServiceIsUnavailable() {
        stub.replyRaw("/route", 400, "{\"error_code\":154,\"error\":\"Path distance exceeds the max distance limit\",\"status_code\":400}");

        assertThatThrownBy(() -> client.truckRoute(List.of(HCM, PHNOM_PENH), 15, 80, LocalDateTime.parse("2026-11-02T05:00")))
                .isInstanceOf(RoutingUnavailableException.class)
                .hasMessage("Chưa lấy được tuyến đường, thử lại sau.");
    }

    @Test
    void anHtmlErrorPageMeansTheServiceIsUnavailable() {
        stub.replyRaw("/route", 502, "<html><body>Bad Gateway</body></html>");

        assertThatThrownBy(() -> client.truckRoute(List.of(HCM, PHNOM_PENH), 15, 80, LocalDateTime.parse("2026-11-02T05:00")))
                .isInstanceOf(RoutingUnavailableException.class);
    }

    @Test
    void unreachableServerMeansTheServiceIsUnavailable() {
        String closedPort = stub.url();
        stub.close();
        RoadRoutingService offline = new RoadRoutingServiceImpl(closedPort, json);

        assertThatThrownBy(() -> offline.truckRoute(List.of(HCM, PHNOM_PENH), 15, 80, LocalDateTime.parse("2026-11-02T05:00")))
                .isInstanceOf(RoutingUnavailableException.class);
        stub = new RoutingStubServer(); // để @AfterEach đóng được
    }
}
