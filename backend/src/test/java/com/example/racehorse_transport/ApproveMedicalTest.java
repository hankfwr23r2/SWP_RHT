package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.UserRepository;
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

// Chuyên viên duyệt hồ sơ ngựa của đơn chỉ ở bước Thẩm định (under_review). Đơn đã qua bước đó
// (đã báo giá, đã cọc…) hay chưa tới (chưa tiếp nhận) thì bị từ chối, trạng thái và hồ sơ giữ nguyên.
// Yêu cầu khách bổ sung hồ sơ cũng chỉ làm được ở bước Thẩm định.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApproveMedicalTest {

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
    private String specialistToken;
    private String specialistCode;
    private Customer customer;

    @BeforeEach
    void specialistAndCustomer() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        specialistCode = "SP-" + suffix;
        User specialistUser = new User();
        specialistUser.setUsername("chuyenvien" + suffix);
        specialistUser.setEmail("chuyenvien" + suffix + "@example.com");
        specialistUser.setPasswordHash(passwordEncoder.encode("Test@123"));
        specialistUser = users.save(specialistUser);
        Staff specialist = new Staff();
        specialist.setUser(specialistUser);
        specialist.setRole("SPECIALIST");
        specialist.setStaffCode(specialistCode);
        specialist.setEmploymentStatus("ACTIVE");
        specialist.setHireDate(LocalDate.now());
        staff.save(specialist);

        User customerUser = new User();
        customerUser.setUsername("khach" + suffix);
        customerUser.setEmail("khach" + suffix + "@example.com");
        customerUser.setPasswordHash(passwordEncoder.encode("Test@123"));
        customerUser = users.save(customerUser);
        customer = new Customer();
        customer.setUser(customerUser);
        customer.setFullName("Khach Test");
        customer = customers.save(customer);

        HttpResponse<String> login = post("/auth/staff/login", null,
                "{\"username\":\"%s\",\"password\":\"Test@123\"}".formatted(specialistUser.getEmail()));
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        specialistToken = json.readTree(login.body()).path("data").path("accessToken").asText();
    }

    @Test
    void rejectedOnceTheCustomerHasPaidTheDeposit() throws Exception {
        Booking booking = booking("waybill_issued", "");

        HttpResponse<String> response = approve(booking);

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertUnchanged(booking, "waybill_issued");
    }

    @Test
    void rejectedWhileWaitingForTheCommercialQuote() throws Exception {
        Booking booking = booking("pending_commercial", "");

        HttpResponse<String> response = approve(booking);

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertUnchanged(booking, "pending_commercial");
    }

    @Test
    void rejectedBeforeTheManagerAcceptsTheBooking() throws Exception {
        Booking booking = booking("pending_intake", "");

        HttpResponse<String> response = approve(booking);

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        assertUnchanged(booking, "pending_intake");
    }

    @Test
    void approvedDuringReviewWaitsForTheVehiclePlan() throws Exception {
        Booking booking = booking("under_review", "");

        HttpResponse<String> response = approve(booking);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("under_review");
        assertThat(json.readTree(saved.getDetailsJson()).path("medical").path("status").asText()).isEqualTo("approved");
    }

    @Test
    void approvedDuringReviewGoesToQuoteWhenThePlanIsConfirmed() throws Exception {
        Booking booking = booking("under_review",
                ",\"trips\":[{\"tripId\":\"TRP-1\",\"vehicleId\":\"VH-001\",\"horseIds\":[\"H-1\"]}],\"plan\":{\"at\":1,\"by\":\"Điều phối\",\"note\":\"\"}");

        HttpResponse<String> response = approve(booking);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("pending_commercial");
    }

    @Test
    void resubmissionRequestRejectedOnceTheCustomerHasPaidTheDeposit() throws Exception {
        Booking booking = booking("waybill_issued", "");

        HttpResponse<String> response = requestResubmission(booking);

        assertThat(response.statusCode()).as(response.body()).isBetween(400, 499);
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("waybill_issued");
        assertThat(json.readTree(saved.getDetailsJson()).path("medical").path("status").asText()).isNotEqualTo("resubmit");
    }

    @Test
    void resubmissionRequestedDuringReview() throws Exception {
        Booking booking = booking("under_review", "");

        HttpResponse<String> response = requestResubmission(booking);

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("under_review");
        assertThat(json.readTree(saved.getDetailsJson()).path("medical").path("status").asText()).isEqualTo("resubmit");
    }

    private HttpResponse<String> requestResubmission(Booking booking) throws Exception {
        return post("/specialist/bookings/" + booking.getId() + "/request-resubmission", specialistToken,
                "{\"reason\":\"Sổ tiêm hết hạn\",\"items\":[{\"horseId\":\"H-1\",\"doc\":\"vaccine\",\"reason\":\"Hết hạn\"}]}");
    }

    private void assertUnchanged(Booking booking, String status) throws Exception {
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(status);
        assertThat(json.readTree(saved.getDetailsJson()).path("medical").path("status").asText()).isNotEqualTo("approved");
    }

    private Booking booking(String status, String extraDetails) {
        String intake = "\"intake\":{\"at\":1,\"by\":\"Quản lý\",\"specialist\":{\"id\":\"%s\",\"name\":\"Chuyên viên\"},\"coordinator\":{\"id\":\"CO-1\",\"name\":\"Điều phối\"}}"
                .formatted(specialistCode);
        return bookings.save(Booking.builder()
                .customerID(customer)
                .status(status)
                .bookingType("domestic")
                .bookingDate(Instant.now().minus(5, ChronoUnit.DAYS))
                .departureDate(Instant.now().plus(25, ChronoUnit.DAYS))
                .pickupAddress("Kho Đồng Nai")
                .dropoffAddress("Hà Nội")
                .detailsJson("{" + intake + extraDetails + "}")
                .build());
    }

    private HttpResponse<String> approve(Booking booking) throws Exception {
        return post("/specialist/bookings/" + booking.getId() + "/verify-horse", specialistToken, "{\"note\":\"Hồ sơ đầy đủ\"}");
    }

    private HttpResponse<String> post(String path, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
