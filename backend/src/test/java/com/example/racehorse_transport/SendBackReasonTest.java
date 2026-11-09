package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Quản lý trả đơn đang chờ duyệt báo giá về Chuyên viên, Điều phối viên hoặc cả hai.
// Mỗi bên nhận một lý do riêng và thấy lý do đó khi mở đơn; trả cả hai bên thì phải ghi đủ hai lý do.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockTurnstileVerification
class SendBackReasonTest {

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
    private PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private String managerToken;
    private String specialistToken;
    private String coordinatorToken;
    private String specialistCode;
    private String coordinatorCode;
    private Customer customer;

    @BeforeEach
    void staffAndCustomer() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        managerToken = login(staffAccount("quanly" + suffix, "MANAGER", "MG-" + suffix));
        specialistCode = "SP-" + suffix;
        specialistToken = login(staffAccount("chuyenvien" + suffix, "SPECIALIST", specialistCode));
        coordinatorCode = "CO-" + suffix;
        coordinatorToken = login(staffAccount("dieuphoi" + suffix, "COORDINATOR", coordinatorCode));

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
    void bothSidesSeeTheirOwnReason() throws Exception {
        Booking booking = waitingForQuote();

        HttpResponse<String> response = sendBack(booking,
                "{\"to\":\"both\",\"specialistReason\":\"Hộ chiếu ngựa mờ, cần khách chụp lại\",\"coordinatorReason\":\"Xe Light không đủ ngăn, chọn xe Medium\"}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("under_review");

        JsonNode seenBySpecialist = detail("/specialist/bookings/" + booking.getId(), specialistToken);
        assertThat(seenBySpecialist.path("sendBack").path("specialist").asText()).isEqualTo("Hộ chiếu ngựa mờ, cần khách chụp lại");

        JsonNode seenByCoordinator = detail("/coordinator/bookings/" + booking.getId(), coordinatorToken);
        assertThat(seenByCoordinator.path("sendBack").path("coordinator").asText()).isEqualTo("Xe Light không đủ ngăn, chọn xe Medium");
    }

    @Test
    void sendingBackToOneSideOnlyRecordsThatSide() throws Exception {
        Booking booking = waitingForQuote();

        HttpResponse<String> response = sendBack(booking, "{\"to\":\"coordinator\",\"coordinatorReason\":\"Lộ trình đi qua đèo, đổi tuyến\"}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        JsonNode seenByCoordinator = detail("/coordinator/bookings/" + booking.getId(), coordinatorToken);
        assertThat(seenByCoordinator.path("sendBack").path("coordinator").asText()).isEqualTo("Lộ trình đi qua đèo, đổi tuyến");
        assertThat(seenByCoordinator.path("sendBack").path("specialist").isMissingNode()
                || seenByCoordinator.path("sendBack").path("specialist").isNull()).isTrue();
    }

    @Test
    void sendingBackToBothNeedsAReasonForEachSide() throws Exception {
        Booking booking = waitingForQuote();

        HttpResponse<String> response = sendBack(booking, "{\"to\":\"both\",\"specialistReason\":\"Hộ chiếu mờ\"}");

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("pending_commercial");
    }

    @Test
    void theReasonGoesAwayOnceThatSideHasRedoneItsPart() throws Exception {
        Booking booking = waitingForQuote();
        sendBack(booking, "{\"to\":\"specialist\",\"specialistReason\":\"Sổ tiêm thiếu trang\"}");

        HttpResponse<String> approve = post("/specialist/bookings/" + booking.getId() + "/verify-horse", specialistToken, "{\"note\":\"Đã đủ\"}");

        assertThat(approve.statusCode()).as(approve.body()).isEqualTo(200);
        JsonNode seenBySpecialist = detail("/specialist/bookings/" + booking.getId(), specialistToken);
        assertThat(seenBySpecialist.path("sendBack").path("specialist").isMissingNode()
                || seenBySpecialist.path("sendBack").path("specialist").isNull()).isTrue();
    }

    private Booking waitingForQuote() {
        String details = ("{\"intake\":{\"at\":1,\"by\":\"Quản lý\",\"specialist\":{\"id\":\"%s\",\"name\":\"Chuyên viên\"},"
                + "\"coordinator\":{\"id\":\"%s\",\"name\":\"Điều phối\"}},"
                + "\"medical\":{\"status\":\"approved\"},"
                + "\"trips\":[{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[\"H-1\"]}],"
                + "\"plan\":{\"at\":1,\"by\":\"Điều phối\",\"note\":\"\"}}").formatted(specialistCode, coordinatorCode);
        return bookings.save(Booking.builder()
                .customerID(customer)
                .status("pending_commercial")
                .bookingType("domestic")
                .bookingDate(Instant.now().minus(5, ChronoUnit.DAYS))
                .departureDate(Instant.now().plus(25, ChronoUnit.DAYS))
                .pickupAddress("Kho Đồng Nai")
                .dropoffAddress("Hà Nội")
                .detailsJson(details)
                .build());
    }

    private HttpResponse<String> sendBack(Booking booking, String body) throws Exception {
        return post("/manager/bookings/" + booking.getId() + "/send-back", managerToken, body);
    }

    private JsonNode detail(String path, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body()).path("data");
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
        HttpResponse<String> login = post("/auth/staff/login", null,
                "{\"username\":\"%s\",\"password\":\"Test@123\"}".formatted(user.getEmail()));
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        return json.readTree(login.body()).path("data").path("accessToken").asText();
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
