package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.SystemConfig;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.RouteCacheRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.SystemConfigRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.routing.RoutingStubServer;
import com.example.racehorse_transport.routing.RoutingStubServer.Recorded;
import com.example.racehorse_transport.service.SystemConfigService;
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

// Điều phối xem trước lộ trình xe tải: đường thật của xe tải (Valhalla, cả 3 nước), phí cầu đường phần Việt Nam (Vietmap),
// lịch trình có nghỉ 30 phút sau mỗi 4 giờ lái, nghỉ đêm sau 10 giờ lái, chờ cửa khẩu mở (07:30–16:30) và 3 giờ thủ tục.
// Valhalla và Vietmap là máy chủ giả trả ĐÚNG mẫu phản hồi thật đã lưu (chỗ giả lập duy nhất); DB là Postgres thật.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoutePreviewTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final RoutingStubServer STUB = new RoutingStubServer();
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String PICKUP_HCM = "{\"name\":\"Kho TP.HCM\",\"lat\":10.7769,\"lng\":106.7009,\"country\":\"VN\"}";
    private static final String DROPOFF_PHNOM_PENH = "{\"name\":\"Phnom Penh\",\"lat\":11.5564,\"lng\":104.9282,\"country\":\"KH\"}";
    private static final String DROPOFF_HANOI = "{\"name\":\"Hà Nội\",\"lat\":21.0278,\"lng\":105.8342,\"country\":\"VN\"}";

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
    private SystemConfigRepository systemConfigs;
    @Autowired
    private SystemConfigService systemConfigService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String coordinatorToken;
    private String driverToken;
    private Location mocBai;
    private Vehicle fourStallTruck;

    @BeforeEach
    void setUp() throws Exception {
        routeCache.deleteAll();
        STUB.clearRequests();
        STUB.reply("/route", 200, "valhalla-truck-hcm-mocbai-phnompenh.json");
        STUB.reply("/route/v4", 200, "vietmap-v4-hcm-hanoi-toll.json");
        coordinatorToken = loginAs("COORDINATOR");
        driverToken = loginAs("DRIVER");
        mocBai = location("BorderGate", "Cửa khẩu Mộc Bài (test)", 11.0796, 106.1572, 450, 990);
        fourStallTruck = vehicles.save(Vehicle.builder()
                .vehicleType("Xe chuyên dụng 4 ngăn")
                .licensePlate("51C-" + UUID.randomUUID().toString().substring(0, 5))
                .status("ACTIVE")
                .build());
    }

    @Test
    void internationalRouteGoesThroughTheGateWithRealTruckTimes() throws Exception {
        HttpResponse<String> response = preview(PICKUP_HCM, DROPOFF_PHNOM_PENH, List.of(mocBai.getId()));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode data = json.readTree(response.body()).path("data");
        assertThat(data.path("source").asText()).isEqualTo("valhalla");
        assertThat(data.path("km").asDouble()).isEqualTo(238.7);
        assertThat(data.path("driveHours").asDouble()).isEqualTo(5.69);
        assertThat(data.path("legs")).hasSize(2);
        assertThat(data.path("legs").get(0).path("from").asText()).isEqualTo("Kho TP.HCM");
        assertThat(data.path("legs").get(0).path("to").asText()).isEqualTo("Cửa khẩu Mộc Bài (test)");
        assertThat(data.path("legs").get(0).path("km").asDouble()).isEqualTo(74.8);
        assertThat(data.path("legs").get(0).path("driveHours").asDouble()).isEqualTo(2.23);
        assertThat(data.path("legs").get(1).path("km").asDouble()).isEqualTo(164.0);
        assertThat(data.path("legs").get(1).path("driveHours").asDouble()).isEqualTo(3.46);
        assertThat(types(data)).containsExactly("DRIVE", "BORDER_WAIT", "BORDER_PROCESSING", "DRIVE");
        assertThat(data.path("borderArrivalAt").asLong()).isEqualTo(epoch("2026-11-02T07:13:49"));
        assertThat(data.path("arrivalAt").asLong()).isEqualTo(epoch("2026-11-02T13:57:52"));
        assertThat(data.path("crewDays").asInt()).isEqualTo(1);
        assertThat(data.path("toll").path("amountVnd").asLong()).isEqualTo(3_111_000L);
        assertThat(data.path("toll").path("source").asText()).isEqualTo("vietmap");
        assertThat(data.path("warnings")).isEmpty();
        assertThat(data.path("path").size()).isBetween(2, 500);
    }

    @Test
    void asksValhallaForTheVehicleWeightAndVietmapOnlyForTheVietnamesePart() throws Exception {
        preview(PICKUP_HCM, DROPOFF_PHNOM_PENH, List.of(mocBai.getId()));

        JsonNode sent = json.readTree(STUB.requests("/route").getFirst().body());
        assertThat(sent.path("costing").asText()).isEqualTo("truck");
        assertThat(sent.path("costing_options").path("truck").path("weight").asDouble()).isEqualTo(8.0);
        assertThat(sent.path("costing_options").path("truck").path("top_speed").asInt()).isEqualTo(80);
        assertThat(sent.path("date_time").path("value").asText()).isEqualTo("2026-11-02T05:00");
        Recorded toll = STUB.requests("/route/v4").getFirst();
        assertThat(toll.params().get("point")).containsExactly("10.7769,106.7009", "11.0796,106.1572");
        assertThat(toll.params().get("capacity")).containsExactly("8000");
    }

    @Test
    void topSpeedSetByTheAdminIsUsed() throws Exception {
        systemConfigService.getConfig("TRUCK_TOP_SPEED_KMH"); // nạp giá trị mặc định nếu DB test chưa có
        SystemConfig topSpeed = systemConfigs.findById("TRUCK_TOP_SPEED_KMH").orElseThrow();
        topSpeed.setConfigValue("70");
        systemConfigs.save(topSpeed);
        try {
            preview(PICKUP_HCM, DROPOFF_PHNOM_PENH, List.of(mocBai.getId()));

            JsonNode sent = json.readTree(STUB.requests("/route").getFirst().body());
            assertThat(sent.path("costing_options").path("truck").path("top_speed").asInt()).isEqualTo(70);
        } finally {
            topSpeed.setConfigValue("80");
            systemConfigs.save(topSpeed);
        }
    }

    @Test
    void longDomesticTripRestsEveryFourHoursAndStopsForTheNight() throws Exception {
        STUB.reply("/route", 200, "valhalla-truck-hcm-hanoi.json");
        // Trạm nghỉ đặt cạnh vị trí xe sau 4 giờ lái đầu tiên (km 273,1 trên đường vẽ mẫu ≈ 11.4755, 108.8257)
        location("RestStop", "Trạm nghỉ Phan Rang (test)", 11.48, 108.826, null, null);

        HttpResponse<String> response = preview(PICKUP_HCM, DROPOFF_HANOI, List.of());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode data = json.readTree(response.body()).path("data");
        assertThat(types(data)).containsExactly(
                "DRIVE", "ROADSIDE_REST", "DRIVE", "ROADSIDE_REST", "DRIVE", "OVERNIGHT",
                "DRIVE", "ROADSIDE_REST", "DRIVE", "ROADSIDE_REST", "DRIVE", "OVERNIGHT", "DRIVE");
        JsonNode firstRest = data.path("schedule").get(1);
        assertThat(firstRest.path("startAt").asLong()).isEqualTo(epoch("2026-11-02T09:00"));
        assertThat(firstRest.path("endAt").asLong()).isEqualTo(epoch("2026-11-02T09:30"));
        assertThat(firstRest.path("place").asText()).isEqualTo("Trạm nghỉ Phan Rang (test)");
        JsonNode firstNight = data.path("schedule").get(5);
        assertThat(firstNight.path("startAt").asLong()).isEqualTo(epoch("2026-11-02T16:00"));
        assertThat(firstNight.path("endAt").asLong()).isEqualTo(epoch("2026-11-03T02:00"));
        assertThat(firstNight.path("place").asText()).isNotBlank();
        assertThat(data.path("arrivalAt").asLong()).isEqualTo(epoch("2026-11-04T02:54:54"));
        assertThat(data.path("crewDays").asInt()).isEqualTo(3);
        assertThat(data.path("borderArrivalAt").isNumber()).isFalse(); // tuyến nội địa không qua cửa khẩu
        Recorded toll = STUB.requests("/route/v4").getFirst();
        assertThat(toll.params().get("point")).containsExactly("10.7769,106.7009", "21.0278,105.8342");
    }

    @Test
    void secondPreviewOfTheSameRouteUsesTheStoredResult() throws Exception {
        preview(PICKUP_HCM, DROPOFF_PHNOM_PENH, List.of(mocBai.getId()));
        HttpResponse<String> again = preview(PICKUP_HCM, DROPOFF_PHNOM_PENH, List.of(mocBai.getId()));

        assertThat(again.statusCode()).isEqualTo(200);
        assertThat(json.readTree(again.body()).path("data").path("toll").path("amountVnd").asLong()).isEqualTo(3_111_000L);
        assertThat(STUB.requests("/route")).hasSize(1);
        assertThat(STUB.requests("/route/v4")).hasSize(1);
    }

    @Test
    void gateWithoutCoordinatesIsRejectedByName() throws Exception {
        Location xaMat = location("BorderGate", "Cửa khẩu Xa Mát (test)", null, null, 450, 990);

        HttpResponse<String> response = preview(PICKUP_HCM, DROPOFF_PHNOM_PENH, List.of(xaMat.getId()));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(message(response)).contains("Cửa khẩu Xa Mát (test)").contains("chưa có tọa độ");
        assertThat(STUB.requests("/route")).isEmpty();
    }

    @Test
    void routeThroughTwoGatesIsRejected() throws Exception {
        Location tinhBien = location("BorderGate", "Cửa khẩu Tịnh Biên (test)", 10.6, 104.95, 450, 990);

        HttpResponse<String> response = preview(PICKUP_HCM, DROPOFF_PHNOM_PENH, List.of(mocBai.getId(), tinhBien.getId()));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(message(response)).isEqualTo("Tuyến chỉ đi qua một cửa khẩu.");
    }

    @Test
    void noTruckPathAsksTheCoordinatorToChooseAnotherGateOrTruck() throws Exception {
        STUB.reply("/route", 400, "valhalla-truck-no-route-sea.json");

        HttpResponse<String> response = preview(PICKUP_HCM, DROPOFF_PHNOM_PENH, List.of(mocBai.getId()));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(422);
        assertThat(message(response)).contains("Không tìm được đường cho xe tải");
    }

    @Test
    void valhallaDownIsAServiceUnavailableErrorAndIsNotRemembered() throws Exception {
        STUB.replyRaw("/route", 502, "<html><body>Bad Gateway</body></html>");

        HttpResponse<String> down = preview(PICKUP_HCM, DROPOFF_PHNOM_PENH, List.of(mocBai.getId()));

        assertThat(down.statusCode()).as(down.body()).isEqualTo(503);
        assertThat(message(down)).isEqualTo("Chưa lấy được tuyến đường, thử lại sau.");

        STUB.reply("/route", 200, "valhalla-truck-hcm-mocbai-phnompenh.json");
        HttpResponse<String> back = preview(PICKUP_HCM, DROPOFF_PHNOM_PENH, List.of(mocBai.getId()));
        assertThat(back.statusCode()).as(back.body()).isEqualTo(200);
    }

    @Test
    void vietmapDownStillGivesTheRouteWithAWarning() throws Exception {
        STUB.replyRaw("/route/v4", 500, "<html>error</html>");

        HttpResponse<String> response = preview(PICKUP_HCM, DROPOFF_PHNOM_PENH, List.of(mocBai.getId()));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode data = json.readTree(response.body()).path("data");
        assertThat(data.path("toll").path("amountVnd").isNumber()).isFalse(); // chưa có số tiền phí
        assertThat(data.path("warnings").get(0).asText()).isEqualTo("Chưa lấy được phí cầu đường (Vietmap).");
        assertThat(data.path("legs")).hasSize(2);
    }

    @Test
    void missingDepartureTimeIsRejectedWithoutCallingValhalla() throws Exception {
        String body = "{\"pickup\":%s,\"dropoff\":%s,\"stopLocationIds\":[],\"vehicleId\":\"%s\"}"
                .formatted(PICKUP_HCM, DROPOFF_HANOI, fourStallTruck.getVehicleCode());

        HttpResponse<String> response = post("/coordinator/route-preview", coordinatorToken, body);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(STUB.requests("/route")).isEmpty();
    }

    @Test
    void latitudeOutOfRangeIsRejected() throws Exception {
        String badPickup = "{\"name\":\"Sai tọa độ\",\"lat\":120,\"lng\":106.7,\"country\":\"VN\"}";

        HttpResponse<String> response = preview(badPickup, DROPOFF_HANOI, List.of());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(STUB.requests("/route")).isEmpty();
    }

    @Test
    void driversCannotPreviewRoutes() throws Exception {
        HttpResponse<String> response = post("/coordinator/route-preview", driverToken, previewBody(PICKUP_HCM, DROPOFF_HANOI, List.of()));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    private HttpResponse<String> preview(String pickup, String dropoff, List<Integer> stopIds) throws Exception {
        return post("/coordinator/route-preview", coordinatorToken, previewBody(pickup, dropoff, stopIds));
    }

    private String previewBody(String pickup, String dropoff, List<Integer> stopIds) {
        return "{\"pickup\":%s,\"dropoff\":%s,\"stopLocationIds\":%s,\"vehicleId\":\"%s\",\"departAt\":%d}"
                .formatted(pickup, dropoff, stopIds, fourStallTruck.getVehicleCode(), epoch("2026-11-02T05:00"));
    }

    private static long epoch(String local) {
        return LocalDateTime.parse(local).atZone(VN).toInstant().toEpochMilli();
    }

    private static List<String> types(JsonNode data) {
        List<String> types = new ArrayList<>();
        data.path("schedule").forEach(event -> types.add(event.path("type").asText()));
        return types;
    }

    private Location location(String type, String name, Double lat, Double lng, Integer opening, Integer closing) {
        Location location = new Location();
        location.setId(locations.findMaxId() + 1);
        location.setLocationType(type);
        location.setLocationName(name);
        location.setAddress("Địa chỉ test");
        location.setCity("TP.HCM");
        location.setProvince("TP.HCM");
        location.setCountry("Việt Nam");
        location.setLatitude(lat);
        location.setLongitude(lng);
        location.setOpeningMinutes(opening);
        location.setClosingMinutes(closing);
        location.setStatus("ACTIVE");
        return locations.save(location);
    }

    private String loginAs(String role) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(role.toLowerCase() + suffix);
        user.setEmail(role.toLowerCase() + suffix + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("Test@123"));
        user = users.save(user);
        Staff member = new Staff();
        member.setUser(user);
        member.setRole(role);
        member.setStaffCode(role.substring(0, 2) + "-" + suffix);
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
