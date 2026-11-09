package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRepository;
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

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Báo cáo chuyến của Quản lý chỉ dùng dữ liệu thật của chuyến: giờ dự kiến và giờ check-in thực tế từng mốc,
// đúng/trễ giờ giao, nhật ký an sinh hộ tống gửi ở trạm, sự cố và chi phí, hộ tống của chuyến, điểm khách chấm.
// Khách chưa chấm thì để trống; chuyến chưa giao ngựa thì chưa có trong báo cáo.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockTurnstileVerification
class TripReportRealDataTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;
    @Autowired
    private UserRepository users;
    @Autowired
    private StaffRepository staff;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private BookingRepository bookings;
    @Autowired
    private IncidentRepository incidents;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String managerToken;
    private String escortCode;
    private String escortUsername;
    private Customer customer;

    // Mốc thời gian của chuyến mẫu: xuất phát 06:00, nghỉ trạm, giao dự kiến 14:00 nhưng tới 14:40
    private final long start = Instant.parse("2026-11-01T23:00:00Z").toEpochMilli(); // 06:00 giờ VN
    private final long restPlanned = start + hours(4);
    private final long restArrived = restPlanned + minutes(10);
    private final long restLeft = restArrived + minutes(30);
    private final long deliveryPlanned = start + hours(8);
    private final long deliveredAt = deliveryPlanned + minutes(40);

    @BeforeEach
    void managerEscortAndCustomer() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        managerToken = login(staffAccount("quanly" + suffix, "MANAGER", "MG-" + suffix));
        escortCode = "ES-" + suffix;
        escortUsername = staffAccount("hotong" + suffix, "ESCORT", escortCode).getUsername();

        User customerUser = new User();
        customerUser.setUsername("khach" + suffix);
        customerUser.setEmail("khach" + suffix + "@example.com");
        customerUser.setPasswordHash(passwordEncoder.encode("Test@123"));
        customerUser = users.save(customerUser);
        customer = new Customer();
        customer.setUser(customerUser);
        customer.setFullName("Khach Test");
        customer = customers.save(customer);
    }

    @Test
    void reportShowsWhatReallyHappenedOnTheTrip() throws Exception {
        Booking booking = deliveredBooking("delivered_pending_settlement", "");
        incidents.save(Incident.builder().bookingID(booking).tripId("TRP-1").incidentName("Ngựa sốt nhẹ")
                .incidentType("HORSE_HEALTH").status("resolved").createdAt(Instant.ofEpochMilli(restArrived))
                .expensesJson("[{\"id\":\"e1\",\"category\":\"vet_fee\",\"label\":\"Thú y\",\"amount\":500000,\"payer\":\"customer\"}]")
                .build());

        JsonNode report = reportOf(booking);

        assertThat(report.path("completedAt").asLong()).isEqualTo(deliveredAt);
        assertThat(report.path("plannedAt").asLong()).isEqualTo(deliveryPlanned);
        assertThat(report.path("otd").asText()).isEqualTo("late");
        assertThat(report.path("otdMinutes").asInt()).isEqualTo(40);
        assertThat(report.path("totalTime").asText()).isEqualTo("8 giờ 40 phút");
        assertThat(report.path("escort").asText()).contains(escortUsername);

        JsonNode legs = report.path("legs");
        assertThat(legs).hasSize(2);
        assertThat(legs.get(1).path("route").asText()).isEqualTo("Trạm nghỉ Phan Thiết → Trường đua Đà Lạt");
        assertThat(legs.get(1).path("plannedEnd").asLong()).isEqualTo(deliveryPlanned);
        assertThat(legs.get(1).path("actualEnd").asLong()).isEqualTo(deliveredAt);
        assertThat(legs.get(1).path("actualStart").asLong()).isEqualTo(restLeft);

        JsonNode health = report.path("health");
        assertThat(health).hasSize(1);
        assertThat(health.get(0).get(0).asLong()).isEqualTo(restArrived + minutes(5));
        assertThat(health.get(0).get(1).asText()).contains("Trạm nghỉ Phan Thiết").contains("Căng thẳng");
        assertThat(health.get(0).get(2).asBoolean()).isFalse();

        assertThat(texts(report.path("events"))).anyMatch(text -> text.contains("Sự cố: Ngựa sốt nhẹ"));
        assertThat(texts(report.path("events"))).anyMatch(text -> text.contains("Giao ngựa"));
        assertThat(report.path("incidentCost").asLong()).isEqualTo(500000);
        assertThat(report.path("incidentNote").asText()).contains("Ngựa sốt nhẹ");

        assertThat(report.path("rating").isNull()).isTrue(); // khách chưa chấm: không tự điền 5 sao
        assertThat(report.path("feedback").isNull()).isTrue();
    }

    @Test
    void ratingIsTheCustomersOwn() throws Exception {
        Booking booking = deliveredBooking("completed", ",\"rating\":{\"stars\":3,\"comment\":\"Giao trễ 40 phút\"}");

        JsonNode report = reportOf(booking);

        assertThat(report.path("rating").asInt()).isEqualTo(3);
        assertThat(report.path("feedback").asText()).isEqualTo("Giao trễ 40 phút");
        assertThat(report.path("incidentCost").asLong()).isZero();
    }

    @Test
    void aTripStillOnTheRoadIsNotReportedYet() throws Exception {
        Booking booking = bookings.save(booking("in_transit",
                ("{\"trips\":[{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[\"H-1\"],\"escortId\":\"%s\","
                        + "\"run\":{\"startedAt\":%d}}]}").formatted(escortCode, start)));

        assertThat(reportList()).noneMatch(item -> ("BK-" + booking.getId()).equals(item.path("id").asText()));
    }

    private Booking deliveredBooking(String status, String extra) {
        String trip = ("{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[\"H-1\"],\"escortId\":\"%s\","
                + "\"run\":{\"startedAt\":%d,\"deliveredAt\":%d,\"checkpoints\":["
                + "{\"id\":\"pickup\",\"type\":\"pickup\",\"label\":\"Trại ngựa Củ Chi\",\"plannedAt\":%d,\"arrivedAt\":%d,\"doneAt\":%d},"
                + "{\"id\":\"rest-1\",\"type\":\"rest\",\"label\":\"Trạm nghỉ Phan Thiết\",\"plannedAt\":%d,\"arrivedAt\":%d,\"doneAt\":%d},"
                + "{\"id\":\"delivery\",\"type\":\"delivery\",\"label\":\"Trường đua Đà Lạt\",\"plannedAt\":%d,\"arrivedAt\":%d,\"doneAt\":%d}],"
                + "\"welfare\":[{\"id\":\"w1\",\"checkpointId\":\"rest-1\",\"at\":%d,\"by\":\"Hộ tống\",\"condition\":\"stress\",\"waterLiters\":10,\"hay\":true,\"note\":\"Ngựa hơi lo\"}]}}")
                .formatted(escortCode, start, deliveredAt,
                        start - minutes(30), start - minutes(20), start,
                        restPlanned, restArrived, restLeft,
                        deliveryPlanned, deliveredAt, deliveredAt,
                        restArrived + minutes(5));
        return bookings.save(booking(status, "{\"trips\":[" + trip + "]" + extra + "}"));
    }

    private Booking booking(String status, String details) {
        return Booking.builder()
                .customerID(customer)
                .status(status)
                .bookingType("domestic")
                .totalHorses(1)
                .bookingDate(Instant.now().minus(30, ChronoUnit.DAYS))
                .departureDate(Instant.ofEpochMilli(start))
                .pickupAddress("Trại ngựa Củ Chi")
                .dropoffAddress("Trường đua Đà Lạt")
                .detailsJson(details)
                .build();
    }

    private JsonNode reportOf(Booking booking) throws Exception {
        for (JsonNode item : reportList()) {
            if (("BK-" + booking.getId()).equals(item.path("id").asText())) return item;
        }
        throw new AssertionError("Không thấy BK-" + booking.getId() + " trong báo cáo chuyến");
    }

    private List<JsonNode> reportList() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/trip-reports"))
                .header("Authorization", "Bearer " + managerToken).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        List<JsonNode> items = new ArrayList<>();
        json.readTree(response.body()).path("data").forEach(items::add);
        return items;
    }

    private static List<String> texts(JsonNode rows) {
        List<String> out = new ArrayList<>();
        rows.forEach(row -> out.add(row.get(1).asText()));
        return out;
    }

    private static long hours(long h) {
        return h * 3_600_000L;
    }

    private static long minutes(long m) {
        return m * 60_000L;
    }

    private User staffAccount(String username, String role, String code) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(passwordEncoder.encode("Test@123"));
        user = users.save(user);
        Staff member = new Staff();
        member.setUser(user);
        member.setRole(role);
        member.setStaffCode(code);
        member.setEmploymentStatus("ACTIVE");
        member.setHireDate(LocalDate.now());
        staff.save(member);
        return user;
    }

    private String login(User user) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/staff/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"%s\",\"password\":\"Test@123\"}".formatted(user.getEmail())))
                .build();
        HttpResponse<String> login = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        return json.readTree(login.body()).path("data").path("accessToken").asText();
    }
}
