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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Điều phối xem trước lộ trình: nước phải là mã VN, KH hoặc LA (không nhận tên như "Việt Nam"); tuyến đi nước ngoài
// bắt buộc chọn một cửa khẩu; mã trạm dừng không được rỗng. Dữ liệu sai bị từ chối (400) trước khi gọi Valhalla.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoutePreviewValidationTest {

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
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String coordinatorToken;
    private Location mocBai;
    private Vehicle fourStallTruck;

    @BeforeEach
    void setUp() throws Exception {
        routeCache.deleteAll();
        STUB.clearRequests();
        STUB.reply("/route", 200, "valhalla-truck-hcm-mocbai-phnompenh.json");
        STUB.reply("/route/v4", 200, "vietmap-v4-hcm-hanoi-toll.json");
        coordinatorToken = loginAs("COORDINATOR");
        mocBai = location("BorderGate", "Cửa khẩu Mộc Bài (test)", 11.0796, 106.1572, 450, 990);
        fourStallTruck = vehicles.save(Vehicle.builder()
                .vehicleType("Xe chuyên dụng 4 ngăn")
                .licensePlate("51C-" + UUID.randomUUID().toString().substring(0, 5))
                .status("ACTIVE")
                .build());
    }

    @Test
    void countryNameInsteadOfCodeIsRejectedWithoutCallingValhalla() throws Exception {
        String pickupByName = "{\"name\":\"Kho TP.HCM\",\"lat\":10.7769,\"lng\":106.7009,\"country\":\"Việt Nam\"}";

        HttpResponse<String> response = preview(pickupByName, DROPOFF_HANOI, List.of());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(STUB.requests("/route")).isEmpty();
    }

    @Test
    void crossBorderRouteWithoutGateIsRejectedWithoutCallingValhalla() throws Exception {
        HttpResponse<String> response = preview(PICKUP_HCM, DROPOFF_PHNOM_PENH, List.of());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(message(response)).isEqualTo("Tuyến đi nước ngoài cần chọn một cửa khẩu.");
        assertThat(STUB.requests("/route")).isEmpty();
    }

    @Test
    void crossBorderRouteWithTheGateIsStillAccepted() throws Exception {
        HttpResponse<String> response = preview(PICKUP_HCM, DROPOFF_PHNOM_PENH, List.of(mocBai.getId()));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    @Test
    void nullStopIdIsRejectedWithoutCallingValhalla() throws Exception {
        String body = "{\"pickup\":%s,\"dropoff\":%s,\"stopLocationIds\":[null],\"vehicleId\":\"%s\",\"departAt\":%d}"
                .formatted(PICKUP_HCM, DROPOFF_HANOI, fourStallTruck.getVehicleCode(), epoch("2026-11-02T05:00"));

        HttpResponse<String> response = post("/coordinator/route-preview", coordinatorToken, body);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        assertThat(STUB.requests("/route")).isEmpty();
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
