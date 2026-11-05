package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.RouteCacheRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.routing.RoutingStubServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// Lộ trình xe tải tự nghỉ ở trạm nghỉ thật trên đường (cách đường không quá 5 km, trong khoảng 2–4 giờ lái liên tục,
// chọn trạm xa nhất); đơn nội địa chỉ dừng ở trạm trong nước, không đi qua cửa khẩu, và tuyến không được đi sâu
// vào lãnh thổ Campuchia/Lào. Valhalla/Vietmap là máy chủ giả trả mẫu phản hồi thật; DB là Postgres thật.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoutePreviewStationsTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final RoutingStubServer STUB = new RoutingStubServer();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String HCM = "{\"name\":\"Kho TP.HCM\",\"lat\":10.7769,\"lng\":106.7009,\"country\":\"VN\"}";
    private static final String HANOI = "{\"name\":\"Hà Nội\",\"lat\":21.0278,\"lng\":105.8342,\"country\":\"VN\"}";
    private static final String CHAU_DOC = "{\"name\":\"Kho Châu Đốc\",\"lat\":10.705,\"lng\":105.115,\"country\":\"VN\"}";
    private static final String HCM_DROPOFF = "{\"name\":\"Kho TP.HCM\",\"lat\":10.79,\"lng\":106.70,\"country\":\"VN\"}";
    private static final String BINH_DUONG = "{\"name\":\"Kho Bình Dương\",\"lat\":10.98,\"lng\":106.65,\"country\":\"VN\"}";
    private static final String MOC_BAI_YARD = "{\"name\":\"Bãi xe Mộc Bài\",\"lat\":11.07,\"lng\":106.2,\"country\":\"VN\"}";
    private static final String PHNOM_PENH = "{\"name\":\"Phnom Penh\",\"lat\":11.5564,\"lng\":104.9282,\"country\":\"KH\"}";

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("routing.valhalla.url", STUB::url);
        registry.add("routing.vietmap.url", STUB::url);
        registry.add("routing.vietmap.api-key", () -> "test-key");
    }

    @LocalServerPort
    private int port;
    @Autowired
    private UserRepository users;
    @Autowired
    private StaffRepository staff;
    @Autowired
    private LocationRepository locations;
    @Autowired
    private VehicleRepository vehicles;
    @Autowired
    private RouteCacheRepository routeCache;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String coordinatorToken;
    private Vehicle truck;

    @BeforeEach
    void setUp() throws Exception {
        routeCache.deleteAll();
        locations.findAll().stream()
                .filter(l -> l.getLocationName() != null && l.getLocationName().endsWith("(test)"))
                .forEach(locations::delete);
        // Trạm nghỉ mẫu (nạp sẵn khi khởi động) tắt đi để mỗi kịch bản chỉ dùng trạm do chính test tạo
        locations.findAll().stream()
                .filter(l -> "RestStop".equals(l.getLocationType())
                        && (l.getLocationName() == null || !l.getLocationName().endsWith("(test)")))
                .forEach(l -> {
                    l.setStatus("INACTIVE");
                    locations.save(l);
                });
        STUB.clearRequests();
        STUB.clearConditionalReplies();
        STUB.reply("/route", 200, "valhalla-truck-hcm-hanoi.json");
        STUB.reply("/route/v4", 200, "vietmap-v4-hcm-hanoi-toll.json");
        coordinatorToken = loginAsCoordinator();
        truck = vehicles.save(Vehicle.builder()
                .vehicleType("Xe chuyên dụng 4 ngăn")
                .licensePlate("51C-" + UUID.randomUUID().toString().substring(0, 5))
                .status("ACTIVE")
                .build());
    }

    @Test
    void restsAtTheFarthestStationOnTheRoadWithinTheWindow() throws Exception {
        location("RestStop", "Trạm nghỉ A (test)", "Việt Nam", 10.931994, 107.970642);
        Location b = location("RestStop", "Trạm nghỉ B (test)", "Việt Nam", 11.387189, 108.745661);
        location("RestStop", "Trạm nghỉ lệch đường (test)", "Việt Nam", 11.418047, 108.858244);

        HttpResponse<String> response = preview(HCM, HANOI, List.of());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode schedule = json.readTree(response.body()).path("data").path("schedule");
        JsonNode rest = schedule.get(1);
        assertThat(rest.path("type").asText()).isEqualTo("REST");
        assertThat(rest.path("place").asText()).isEqualTo("Trạm nghỉ B (test)");
        assertThat(rest.path("startAt").asLong()).isCloseTo(epoch("2026-11-02T08:48:39"), within(60_000L));
        assertThat(rest.path("endAt").asLong() - rest.path("startAt").asLong()).isEqualTo(30 * 60_000L);
        assertThat(rest.path("lat").asDouble()).isEqualTo(b.getLatitude());
        assertThat(rest.path("lng").asDouble()).isEqualTo(b.getLongitude());
        assertThat(schedule.get(0).path("type").asText()).isEqualTo("DRIVE");
        assertThat(schedule.get(0).path("km").asDouble()).isCloseTo(260.2, within(0.5));
        JsonNode roadside = schedule.get(3);
        assertThat(roadside.path("type").asText()).isEqualTo("ROADSIDE_REST");
        assertThat(roadside.path("startAt").asLong()).isCloseTo(epoch("2026-11-02T13:18:39"), within(60_000L));
        assertThat(roadside.path("lat").isNumber()).isTrue();
        // Cảnh báo nói rõ xe dừng ở đâu và điều phối có thể làm gì
        assertThat(json.readTree(response.body()).path("data").path("warnings").toString())
                .contains("xe phải nghỉ dọc đường 5 lần (km 533; km 683; km 956; km 1229; km 1366)")
                .contains("Có thể chọn thêm trạm nghỉ trên bản đồ.");
    }

    @Test
    void foreignStationIsNotChosenForADomesticTrip() throws Exception {
        location("RestStop", "Trạm Campuchia (test)", "Campuchia", 11.387189, 108.745661);

        HttpResponse<String> response = preview(HCM, HANOI, List.of());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode first = json.readTree(response.body()).path("data").path("schedule").get(1);
        assertThat(first.path("type").asText()).isEqualTo("ROADSIDE_REST");
        assertThat(first.path("startAt").asLong()).isEqualTo(epoch("2026-11-02T09:00"));
    }

    @Test
    void domesticTripCannotStopAtAForeignStation() throws Exception {
        Location foreign = location("RestStop", "Trạm Campuchia (test)", "Campuchia", 11.09, 105.8);

        HttpResponse<String> response = preview(HCM, HANOI, List.of(foreign.getId()));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(message(response)).isEqualTo("Đơn nội địa chỉ dừng ở trạm trong nước: Trạm Campuchia (test).");
        assertThat(STUB.requests("/route")).isEmpty();
    }

    @Test
    void domesticTripCannotGoThroughABorderGate() throws Exception {
        Location gate = location("BorderGate", "Cửa khẩu Mộc Bài (test)", "Việt Nam", 11.0796, 106.1572);

        HttpResponse<String> response = preview(HCM, HANOI, List.of(gate.getId()));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(message(response)).isEqualTo("Đơn nội địa không đi qua cửa khẩu.");
        assertThat(STUB.requests("/route")).isEmpty();
    }

    @Test
    void domesticRouteDetouringThroughCambodiaIsRejectedNamingTheStation() throws Exception {
        STUB.reply("/route", 200, "valhalla-truck-chaudoc-mocbai-hcm.json");
        Location mocBai = location("RestStop", "Trạm nghỉ Mộc Bài (test)", "Việt Nam", 11.07, 106.2);

        HttpResponse<String> response = preview(CHAU_DOC, HCM_DROPOFF, List.of(mocBai.getId()));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(422);
        assertThat(message(response)).isEqualTo(
                "Tuyến nội địa đi qua lãnh thổ Campuchia ở chặng Kho Châu Đốc → Trạm nghỉ Mộc Bài (test). "
                        + "Hãy bỏ hoặc đổi trạm Trạm nghỉ Mộc Bài (test).");
    }

    // Điều phối mở đơn Châu Đốc → Bình Dương, chưa chọn trạm: đường xe tải ngắn nhất cắt qua Svay Rieng (Campuchia).
    // Hệ thống phải tự tìm đường trong Việt Nam: thử ép xe đi qua trạm nghỉ trong nước ít vòng nhất trước (Mộc Bài, vẫn lọt
    // sang Campuchia), rồi trạm kế tiếp (Cao Lãnh, đi trọn trong nước). Điều phối nhận được lộ trình, không thấy lỗi.
    @Test
    void domesticRouteCuttingThroughCambodiaIsKeptInsideVietnamAutomatically() throws Exception {
        STUB.reply("/route", 200, "valhalla-truck-chaudoc-binhduong.json");
        STUB.replyWhenBodyContains("/route", List.of("\"through\"", "\"lat\":11.07"), 200, "valhalla-truck-chaudoc-binhduong-through-mocbai.json");
        STUB.replyWhenBodyContains("/route", List.of("\"through\"", "\"lat\":10.46"), 200, "valhalla-truck-chaudoc-binhduong-through-caolanh.json");
        location("RestStop", "Trạm nghỉ Mộc Bài (test)", "Việt Nam", 11.07, 106.2);
        location("RestStop", "Trạm nghỉ Cao Lãnh (test)", "Việt Nam", 10.46, 105.63);

        HttpResponse<String> response = preview(CHAU_DOC, BINH_DUONG, List.of());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode data = json.readTree(response.body()).path("data");
        assertThat(data.path("km").asDouble()).isCloseTo(236.6, within(0.5));
        assertThat(data.path("legs")).hasSize(1);
        assertThat(data.path("legs").get(0).path("from").asText()).isEqualTo("Kho Châu Đốc");
        assertThat(data.path("legs").get(0).path("to").asText()).isEqualTo("Kho Bình Dương");
        List<RoutingStubServer.Recorded> calls = STUB.requests("/route");
        assertThat(calls).hasSize(3);
        assertThat(calls.get(1).body()).contains("\"lat\":11.07");
        assertThat(calls.get(2).body()).contains("\"lat\":10.46").contains("\"type\":\"through\"");
    }

    // Điều phối bấm chọn Củ Chi trước rồi mới tới Cao Lãnh. Xe phải ghé theo thứ tự trên đường thật
    // (Châu Đốc → Cao Lãnh → Củ Chi → Bình Dương), không chạy theo thứ tự bấm hay theo đường chim bay.
    @Test
    void stationsAreVisitedInTheOrderTheyLieOnTheRoad() throws Exception {
        STUB.reply("/route", 200, "valhalla-truck-chaudoc-binhduong.json");
        STUB.replyWhenBodyContains("/route", List.of("\"lat\":10.46", "\"lat\":10.97"), 200, "valhalla-truck-chaudoc-caolanh-cuchi-binhduong.json");
        Location cuChi = location("RestStop", "Trạm nghỉ Củ Chi (test)", "Việt Nam", 10.97, 106.5);
        Location caoLanh = location("RestStop", "Trạm nghỉ Cao Lãnh (test)", "Việt Nam", 10.46, 105.63);

        HttpResponse<String> response = preview(CHAU_DOC, BINH_DUONG, List.of(cuChi.getId(), caoLanh.getId()));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        List<String> legs = new ArrayList<>();
        json.readTree(response.body()).path("data").path("legs").forEach(l -> legs.add(l.path("to").asText()));
        assertThat(legs).containsExactly("Trạm nghỉ Cao Lãnh (test)", "Trạm nghỉ Củ Chi (test)", "Kho Bình Dương");
        String lastCall = STUB.requests("/route").getLast().body();
        assertThat(lastCall.indexOf("\"lat\":10.46")).isLessThan(lastCall.indexOf("\"lat\":10.97"));
    }

    // Không còn trạm trong nước nào giúp xe tránh được Campuchia: báo đúng chặng và việc điều phối cần làm.
    @Test
    void domesticRouteThatCannotAvoidCambodiaTellsTheCoordinatorWhatToDo() throws Exception {
        STUB.reply("/route", 200, "valhalla-truck-chaudoc-binhduong.json");
        STUB.replyWhenBodyContains("/route", List.of("\"through\"", "\"lat\":11.07"), 200, "valhalla-truck-chaudoc-binhduong-through-mocbai.json");
        location("RestStop", "Trạm nghỉ Mộc Bài (test)", "Việt Nam", 11.07, 106.2);

        HttpResponse<String> response = preview(CHAU_DOC, BINH_DUONG, List.of());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(422);
        assertThat(message(response)).isEqualTo(
                "Tuyến nội địa đi qua lãnh thổ Campuchia ở chặng Kho Châu Đốc → Kho Bình Dương. "
                        + "Hãy chọn thêm một trạm nghỉ ở Việt Nam trên chặng này.");
    }

    // Sự cố trên đường (Flow 5): điều phối cần đường xe tải từ chỗ xe tới điểm kế tiếp. Dùng cùng máy tính đường xe tải
    // và cùng cách giữ tuyến nội địa trong Việt Nam như lúc lập kế hoạch, không dùng đường xe con.
    @Test
    void incidentRoadRouteIsTheTruckRouteKeptInsideVietnam() throws Exception {
        STUB.reply("/route", 200, "valhalla-truck-chaudoc-binhduong.json");
        STUB.replyWhenBodyContains("/route", List.of("\"through\"", "\"lat\":11.07"), 200, "valhalla-truck-chaudoc-binhduong-through-mocbai.json");
        STUB.replyWhenBodyContains("/route", List.of("\"through\"", "\"lat\":10.46"), 200, "valhalla-truck-chaudoc-binhduong-through-caolanh.json");
        location("RestStop", "Trạm nghỉ Mộc Bài (test)", "Việt Nam", 11.07, 106.2);
        location("RestStop", "Trạm nghỉ Cao Lãnh (test)", "Việt Nam", 10.46, 105.63);

        HttpResponse<String> response = post("/coordinator/road-route", coordinatorToken,
                "{\"points\":[{\"lat\":10.705,\"lng\":105.115},{\"lat\":10.98,\"lng\":106.65}],\"alternatives\":false}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode routes = json.readTree(response.body()).path("data").path("routes");
        assertThat(routes).hasSize(1);
        assertThat(routes.get(0).path("km").asDouble()).isCloseTo(236.6, within(0.5));
        assertThat(routes.get(0).path("path").size()).isGreaterThan(10);
        assertThat(STUB.requests("/route").getLast().body()).contains("\"truck\"");
    }

    // Kẹt xe: ngoài đường chính, điều phối thấy thêm đường tránh đi qua một trạm nằm ngoài đường chính (Đức Hòa cách QL22 ~13 km).
    @Test
    void trafficJamGetsADetourThroughAStationOffTheMainRoad() throws Exception {
        STUB.reply("/route", 200, "valhalla-truck-hcm-mocbai.json");
        STUB.replyWhenBodyContains("/route", List.of("\"through\"", "\"lat\":10.88"), 200, "valhalla-truck-hcm-through-duchoa-mocbai.json");
        location("RestStop", "Trạm nghỉ Đức Hòa (test)", "Việt Nam", 10.88, 106.4);

        HttpResponse<String> response = post("/coordinator/road-route", coordinatorToken,
                "{\"points\":[{\"lat\":10.7769,\"lng\":106.7009},{\"lat\":11.07,\"lng\":106.2}],\"alternatives\":true}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode routes = json.readTree(response.body()).path("data").path("routes");
        assertThat(routes).hasSize(2);
        assertThat(routes.get(0).path("km").asDouble()).isCloseTo(72.2, within(0.5));
        assertThat(routes.get(1).path("km").asDouble()).isCloseTo(83.1, within(0.5));
    }

    @Test
    void domesticRouteRunningAlongTheBorderIsAccepted() throws Exception {
        STUB.reply("/route", 200, "valhalla-truck-hcm-mocbai.json");

        HttpResponse<String> response = preview(HCM, MOC_BAI_YARD, List.of());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    @Test
    void stopEventsCarryCoordinatesAndDriveEventsCarryKm() throws Exception {
        STUB.reply("/route", 200, "valhalla-truck-hcm-mocbai-phnompenh.json");
        Location gate = location("BorderGate", "Cửa khẩu Mộc Bài (test)", "Việt Nam", 11.0796, 106.1572);
        gate.setOpeningMinutes(450);
        gate.setClosingMinutes(990);
        locations.save(gate);

        HttpResponse<String> response = preview(HCM, PHNOM_PENH, List.of(gate.getId()));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode schedule = json.readTree(response.body()).path("data").path("schedule");
        List<String> types = new ArrayList<>();
        schedule.forEach(e -> types.add(e.path("type").asText()));
        assertThat(types).containsExactly("DRIVE", "BORDER_WAIT", "BORDER_PROCESSING", "DRIVE");
        assertThat(schedule.get(0).path("km").asDouble()).isEqualTo(74.8);
        assertThat(schedule.get(3).path("km").asDouble()).isEqualTo(164.0);
        assertThat(schedule.get(0).path("lat").isNull()).isTrue();
        assertThat(schedule.get(2).path("lat").asDouble()).isEqualTo(11.0796);
        assertThat(schedule.get(2).path("lng").asDouble()).isEqualTo(106.1572);
    }

    private HttpResponse<String> preview(String pickup, String dropoff, List<Integer> stopIds) throws Exception {
        String body = "{\"pickup\":%s,\"dropoff\":%s,\"stopLocationIds\":%s,\"vehicleId\":\"%s\",\"departAt\":%d}"
                .formatted(pickup, dropoff, stopIds, truck.getVehicleCode(), epoch("2026-11-02T05:00"));
        return post("/coordinator/route-preview", coordinatorToken, body);
    }

    private static long epoch(String local) {
        return LocalDateTime.parse(local).atZone(VN).toInstant().toEpochMilli();
    }

    private Location location(String type, String name, String country, double lat, double lng) {
        Location location = new Location();
        location.setId(locations.findMaxId() + 1);
        location.setLocationType(type);
        location.setLocationName(name);
        location.setAddress("Địa chỉ test");
        location.setCity("Test");
        location.setProvince("Test");
        location.setCountry(country);
        location.setLatitude(lat);
        location.setLongitude(lng);
        location.setStatus("ACTIVE");
        return locations.save(location);
    }

    private String loginAsCoordinator() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername("dieuphoi" + suffix);
        user.setEmail("dieuphoi" + suffix + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("Test@123"));
        user = users.save(user);
        Staff member = new Staff();
        member.setUser(user);
        member.setRole("COORDINATOR");
        member.setStaffCode("CO-" + suffix);
        member.setEmploymentStatus("ACTIVE");
        member.setHireDate(LocalDate.now());
        staff.save(member);
        HttpResponse<String> login = post("/auth/staff/login", null,
                "{\"username\":\"%s\",\"password\":\"Test@123\"}".formatted(user.getEmail()));
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        return json.readTree(login.body()).path("data").path("accessToken").asText();
    }

    private String message(HttpResponse<String> response) throws Exception {
        return json.readTree(response.body()).path("message").asText();
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
