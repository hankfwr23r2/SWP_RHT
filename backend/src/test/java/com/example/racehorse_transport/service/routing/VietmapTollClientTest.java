package com.example.racehorse_transport.service.routing;

import com.example.racehorse_transport.routing.RoutingStubServer;
import com.example.racehorse_transport.routing.RoutingStubServer.Recorded;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;

// Phí cầu đường ở Việt Nam lấy từ Vietmap Route v4 (toll_cost, VND) cho xe tải đúng tải trọng.
// Vietmap lỗi hoặc chưa có key thì trả rỗng để nơi gọi cảnh báo, không làm hỏng cả tuyến.
// Mẫu thật: v4 TP.HCM → Hà Nội, toll_cost = 3.111.000 (gọi ngày 26/10/2026).
class VietmapTollClientTest {

    private static final GeoPoint HCM = new GeoPoint(10.7769, 106.7009);
    private static final GeoPoint MOC_BAI = new GeoPoint(11.0796, 106.1572);

    private final ObjectMapper json = new ObjectMapper();
    private RoutingStubServer stub;

    @BeforeEach
    void start() {
        stub = new RoutingStubServer();
    }

    @AfterEach
    void stop() {
        stub.close();
    }

    @Test
    void readsTheTollOfTheRoute() {
        stub.reply("/route/v4", 200, "vietmap-v4-hcm-hanoi-toll.json");

        assertThat(new VietmapTollClient(stub.url(), "test-key", json).truckToll(HCM, MOC_BAI, 8000))
                .hasValue(3_111_000L);
    }

    @Test
    void asksForATruckWithItsCapacityAndTollsWithoutDepartureTime() {
        stub.reply("/route/v4", 200, "vietmap-v4-hcm-hanoi-toll.json");

        new VietmapTollClient(stub.url(), "test-key", json).truckToll(HCM, MOC_BAI, 8000);

        Recorded sent = stub.requests("/route/v4").getFirst();
        assertThat(sent.method()).isEqualTo("GET");
        assertThat(sent.params().get("apikey")).containsExactly("test-key");
        assertThat(sent.params().get("point")).containsExactly("10.7769,106.7009", "11.0796,106.1572");
        assertThat(sent.params().get("vehicle")).containsExactly("truck");
        assertThat(sent.params().get("capacity")).containsExactly("8000");
        assertThat(sent.params().get("annotations")).containsExactly("toll");
        // Phí không đổi theo giờ; gửi giờ ban ngày thì Vietmap từ chối điểm trong vùng cấm xe tải (POINT_IN_RESTRICTED_ZONE)
        assertThat(sent.params()).doesNotContainKey("time");
    }

    @Test
    void withoutAKeyNothingIsSentAndTheTollIsUnknown() {
        assertThat(new VietmapTollClient(stub.url(), "", json).truckToll(HCM, MOC_BAI, 8000)).isEmpty();
        assertThat(stub.requests("/route/v4")).isEmpty();
    }

    @Test
    void anErrorAnswerLeavesTheTollUnknown() {
        stub.reply("/route/v4", 200, "vietmap-v4-point-not-found-phnompenh.json");

        assertThat(new VietmapTollClient(stub.url(), "test-key", json).truckToll(HCM, MOC_BAI, 8000)).isEmpty();
    }

    @Test
    void aServerErrorLeavesTheTollUnknown() {
        stub.replyRaw("/route/v4", 500, "<html>error</html>");

        assertThat(new VietmapTollClient(stub.url(), "test-key", json).truckToll(HCM, MOC_BAI, 8000)).isEmpty();
    }
}
