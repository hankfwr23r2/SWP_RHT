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

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Báo giá do quản lý phát hành: backend không tin số tiền các dòng có đơn giá chuẩn.
// - "Khoang trên xe" và "Gói thức ăn" phải khớp đơn giá dịch vụ đã chụp lại trong đơn lúc khách đặt
//   (theo chuyến / theo ngựa cộng đúng; theo ngựa-ngày thì nhân số nguyên ngày từ 1 đến 30).
// - Mọi dòng phải có tên và số tiền lớn hơn 0.
// - Cước vận chuyển và nhiên liệu do quản lý nhập, vẫn được đổi.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@MockTurnstileVerification
class QuoteLineVerificationTest {

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
    private Customer customer;

    @BeforeEach
    void managerAndCustomer() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        managerToken = staffLogin("quanly" + suffix, "MANAGER", "MG-" + suffix);
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

    // Hai ngựa: khoang 500.000 đ/ngựa => đúng 1.000.000; thức ăn 100.000 đ/ngựa/ngày => 200.000 mỗi ngày
    @Test
    void quoteWithServiceLinesMatchingTheCatalogIsIssued() throws Exception {
        Booking booking = pendingCommercial();

        HttpResponse<String> response = sendQuote(booking, line("Cước vận chuyển nguyên chuyến", 5_000_000),
                line("Khoang trên xe", 1_000_000), line("Gói thức ăn", 600_000), line("Thủ tục kiểm dịch và hải quan", 300_000));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("awaiting_payment");
        assertThat(json.readTree(saved.getDetailsJson()).path("quote").path("total").asLong()).isEqualTo(6_900_000L);
    }

    @Test
    void transportAndFuelLinesAreTheManagersToSet() throws Exception {
        Booking booking = pendingCommercial();

        HttpResponse<String> response = sendQuote(booking, line("Cước vận chuyển nguyên chuyến", 12_345_000),
                line("Nhiên liệu và BOT", 987_000), line("Khoang trên xe", 1_000_000), line("Gói thức ăn", 200_000));

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }

    @Test
    void stallLineThatDiffersFromTheCatalogIsRejected() throws Exception {
        Booking booking = pendingCommercial();

        HttpResponse<String> response = sendQuote(booking, line("Cước vận chuyển nguyên chuyến", 5_000_000),
                line("Khoang trên xe", 400_000), line("Gói thức ăn", 600_000));

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(messageOf(response)).contains("Khoang trên xe");
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("pending_commercial");
    }

    @Test
    void feedLineThatIsNotAWholeNumberOfDaysIsRejected() throws Exception {
        Booking booking = pendingCommercial();

        HttpResponse<String> response = sendQuote(booking, line("Cước vận chuyển nguyên chuyến", 5_000_000),
                line("Khoang trên xe", 1_000_000), line("Gói thức ăn", 250_000));

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertThat(messageOf(response)).contains("Gói thức ăn");
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("pending_commercial");
    }

    @Test
    void feedLineForMoreThanThirtyDaysIsRejected() throws Exception {
        Booking booking = pendingCommercial();

        HttpResponse<String> thirty = sendQuote(booking, line("Cước vận chuyển nguyên chuyến", 5_000_000),
                line("Khoang trên xe", 1_000_000), line("Gói thức ăn", 6_000_000));
        assertThat(thirty.statusCode()).as(thirty.body()).isEqualTo(200);

        Booking another = pendingCommercial();
        HttpResponse<String> thirtyOne = sendQuote(another, line("Cước vận chuyển nguyên chuyến", 5_000_000),
                line("Khoang trên xe", 1_000_000), line("Gói thức ăn", 6_200_000));
        assertThat(thirtyOne.statusCode()).as(thirtyOne.body()).isBetween(400, 499);
        assertThat(messageOf(thirtyOne)).contains("Gói thức ăn");
    }

    @Test
    void everyLineNeedsANameAndAnAmountAboveZero() throws Exception {
        Booking booking = pendingCommercial();

        HttpResponse<String> noName = sendQuote(booking, line(" ", 5_000_000), line("Khoang trên xe", 1_000_000), line("Gói thức ăn", 200_000));
        assertThat(noName.statusCode()).as(noName.body()).isBetween(400, 499);

        HttpResponse<String> zero = sendQuote(booking, line("Cước vận chuyển nguyên chuyến", 0), line("Khoang trên xe", 1_000_000), line("Gói thức ăn", 200_000));
        assertThat(zero.statusCode()).as(zero.body()).isBetween(400, 499);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("pending_commercial");
    }

    private static String line(String label, long amount) {
        return "{\"label\":\"%s\",\"detail\":\"\",\"calculation\":\"\",\"amount\":%d}".formatted(label, amount);
    }

    private HttpResponse<String> sendQuote(Booking booking, String... lines) throws Exception {
        return post("/manager/bookings/" + booking.getCode() + "/send-quote", managerToken,
                "{\"lines\":[" + String.join(",", lines) + "],\"adjustments\":[]}");
    }

    private static String service(String category, String code, String name, int price, String unit) {
        return "\"%s\":{\"id\":%d,\"serviceCode\":\"%s\",\"serviceName\":\"%s\",\"price\":%d,\"billingUnit\":\"%s\"}"
                .formatted(category, Math.abs(code.hashCode() % 1000), code, name, price, unit);
    }

    private static String horse(String id, String name, String chip) {
        return "{\"horseId\":\"%s\",\"name\":\"%s\",\"microchip\":\"%s\",\"status\":\"ELIGIBLE\",\"insurance\":{\"option\":\"none\"},\"selectedServices\":{%s,%s}}"
                .formatted(id, name, chip, service("stall", "STALL_SINGLE", "Khoang đơn", 500_000, "PER_HORSE"),
                        service("feed", "FEED_PREMIUM", "Gói thức ăn cao cấp", 100_000, "PER_HORSE_DAY"));
    }

    // Đơn chờ duyệt báo giá: chuyên viên đã duyệt hồ sơ, điều phối đã chốt xe, đủ kíp
    private Booking pendingCommercial() {
        String details = "{\"medical\":{\"status\":\"approved\"},\"plan\":{\"at\":1,\"by\":\"Điều phối\",\"note\":\"\"},"
                + "\"horses\":[" + horse("H-1", "Gió Mekong", "VN-800221") + "," + horse("H-2", "Sao Mai", "VN-800222") + "],"
                + "\"trips\":[{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[\"H-1\",\"H-2\"],\"driverId\":\"DR-1\",\"escortId\":\"ES-1\"}]}";
        return bookings.save(Booking.builder()
                .customerID(customer)
                .status("pending_commercial")
                .bookingType("domestic")
                .bookingDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .departureDate(Instant.now().plus(10, ChronoUnit.DAYS))
                .pickupAddress("Kho Đồng Nai")
                .dropoffAddress("Hà Nội")
                .detailsJson(details)
                .build());
    }

    private String staffLogin(String username, String role, String code) throws Exception {
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
        HttpResponse<String> login = post("/auth/staff/login", null,
                "{\"username\":\"%s\",\"password\":\"Test@123\"}".formatted(user.getEmail()));
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        return json.readTree(login.body()).path("data").path("accessToken").asText();
    }

    private String messageOf(HttpResponse<String> response) throws Exception {
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
