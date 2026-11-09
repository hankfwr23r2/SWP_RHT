package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.BillMailService;
import com.example.racehorse_transport.service.BookingScheduleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(BookingScheduleFlowTest.TimeConfiguration.class)
class BookingScheduleFlowTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary
        Clock scheduleTestClock() { return Clock.fixed(Instant.parse("2026-11-30T17:00:00Z"), ZoneOffset.UTC); }
    }

    @LocalServerPort private int port;
    @Autowired private BookingScheduleService schedules;
    @Autowired private BookingRepository bookings;
    @Autowired private CustomerRepository customers;
    @Autowired private UserRepository users;
    @Autowired private JwtUtils jwt;
    @Autowired private JdbcTemplate database;
    @MockitoBean private BillMailService mail;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void customerCanRequestSupportAndRescheduleButOtherUsersCannot() throws Exception {
        Booking booking = order();
        String owner = booking.getCustomerID().getUser().getUsername();
        String token = jwt.generateToken(owner, "CUSTOMER");
        // Approval is blocked before the periodic scan has marked the row.
        assertThat(post("/manager/bookings/" + booking.getId() + "/activate", "{}",
                jwt.generateToken(owner, "ADMIN")).statusCode()).isEqualTo(409);
        schedules.flagOverdue(booking.getId());
        String path = "/customer/bookings/" + booking.getId() + "/schedule-change";
        assertThat(post(path, "{\"departureDate\":\"2026-12-21\"}",
                jwt.generateToken("other-customer", "CUSTOMER")).statusCode()).isEqualTo(403);
        assertThat(post(path, "{\"departureDate\":\"2026-12-21\"}",
                jwt.generateToken(owner, "MANAGER")).statusCode()).isEqualTo(403);
        assertThat(post(path, "{\"departureDate\":\"2026-12-20\"}", token).statusCode()).isEqualTo(400);
        assertThat(post(path, "{}", token).statusCode()).isEqualTo(400);
        assertThat(post(path + "/support", "{}", token).statusCode()).isEqualTo(200);
        assertThat(post(path + "/support", "{}", token).statusCode()).isEqualTo(200);
        assertThat(database.queryForObject("select count(*) from notification where bookingid = ? and title like 'Khách cần hỗ trợ%'",
                Integer.class, "BK-" + booking.getId())).isEqualTo(1);
        HttpResponse<String> response = post(path, "{\"departureDate\":\"2026-12-21\"}", token);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(json.readTree(response.body()).path("data").path("status").asText()).isEqualTo("pending_intake");
        assertThat(bookings.findById(booking.getId()).orElseThrow().getDepartureDate())
                .isEqualTo(Instant.parse("2026-12-20T17:00:00Z"));
    }

    @Test
    void unpaidRequestCanBeCancelledOnlyByItsOwnerWithAReason() throws Exception {
        Booking booking = order();
        schedules.flagOverdue(booking.getId());
        String path = "/customer/bookings/" + booking.getId() + "/schedule-change/cancel";
        String token = jwt.generateToken(booking.getCustomerID().getUser().getUsername(), "CUSTOMER");
        assertThat(post(path, "{\"reason\":\" \"}", token).statusCode()).isEqualTo(400);
        assertThat(post(path, "{\"reason\":\"Không còn nhu cầu\"}", token).statusCode()).isEqualTo(200);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("cancelled");
    }

    private Booking order() {
        String name = "schedule-" + UUID.randomUUID();
        User user = new User(); user.setUsername(name); user.setEmail(name + "@example.com");
        users.saveAndFlush(user);
        Customer customer = new Customer(); customer.setUser(user); customer.setFullName("Khách đổi lịch");
        customer = customers.saveAndFlush(customer);
        Booking booking = Booking.builder().customerID(customer).status("pending_intake")
                .bookingType("domestic").bookingDate(Instant.parse("2026-11-05T03:00:00Z"))
                .departureDate(Instant.parse("2026-11-29T17:00:00Z")).detailsJson("{\"horses\":[]}").build();
        Booking saved = bookings.saveAndFlush(booking);
        saved.setCustomerID(customer);
        return saved;
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
