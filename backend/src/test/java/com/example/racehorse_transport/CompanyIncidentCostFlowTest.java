package com.example.racehorse_transport;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.security.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompanyIncidentCostFlowTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    static { POSTGRES.start(); }
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired CustomerRepository customers;
    @Autowired StaffRepository staff;
    @Autowired BookingRepository bookings;
    @Autowired IncidentRepository incidents;
    @Autowired VehicleRepository vehicles;
    @Autowired PaymentRepository payments;
    @Autowired JwtUtils jwt;
    final ObjectMapper json = new ObjectMapper();
    final HttpClient http = HttpClient.newHttpClient();
    String ownerToken, otherToken, driverToken, managerToken;
    Booking booking;
    Vehicle vehicle;

    @BeforeEach
    void fixtures() {
        String suffix = UUID.randomUUID().toString();
        User owner = user("owner-" + suffix);
        Customer customer = new Customer(); customer.setUser(owner); customer.setFullName("Owner");
        customers.save(customer);
        ownerToken = jwt.generateToken(owner.getUsername(), "CUSTOMER");
        otherToken = jwt.generateToken(user("other-" + suffix).getUsername(), "CUSTOMER");
        User driver = user("driver-" + suffix);
        Staff d = new Staff(); d.setUser(driver); d.setRole("DRIVER"); d.setStaffCode("DR-" + suffix); d.setEmploymentStatus("ACTIVE"); staff.save(d);
        driverToken = jwt.generateToken(driver.getUsername(), "DRIVER");
        User manager = user("manager-" + suffix);
        Staff m = new Staff(); m.setUser(manager); m.setRole("MANAGER"); m.setEmploymentStatus("ACTIVE"); staff.save(m);
        managerToken = jwt.generateToken(manager.getUsername(), "MANAGER");
        vehicle = new Vehicle(); vehicle.setLicensePlate("TEST-" + suffix); vehicle.setStatus("ON_TRIP"); vehicle = vehicles.save(vehicle);
        booking = bookings.save(Booking.builder().customerID(customer).status("delivered_pending_settlement")
                .bookingType("domestic").bookingDate(Instant.now()).detailsJson("""
                    {"trips":[{"tripId":"T-1","vehicleId":"%d","driverId":"%s",
                     "homeReturn":{"eta":9999999999999},"tolls":[{"amount":90000,"photo":"internal-toll.jpg"}]}]}
                    """.formatted(vehicle.getId(), d.getStaffCode())).build());
        Incident incident = Incident.builder().bookingID(booking).tripId("T-1").incidentType("HANDOVER_TIMEOUT")
                .status("resolved").costBearer("COMPANY").estimatedCost(new BigDecimal("1500000"))
                .actualCost(new BigDecimal("800000")).build();
        incident.setExpensesJson("""
            [{"id":"vet","category":"vet_fee","label":"Vet","photo":"private-vet.jpg","amount":500000,"payer":"carrier"},
             {"id":"stable","category":"holding_stable","label":"Stable","photo":"private-stable.jpg","amount":300000,"payer":"carrier"}]
            """);
        incidents.save(incident);
    }

    private User user(String name) { User u = new User(); u.setUsername(name); u.setEmail(name + "@example.com"); return users.save(u); }
    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json");
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void companyReviewsAllCostsWithoutCustomerBillingOrEarlyVehicleRelease() throws Exception {
        String path = "/settlements/" + booking.getId();
        var submitted = request("POST", path + "/submit-expenses", driverToken, "{}");
        assertThat(submitted.statusCode()).as(submitted.body()).isEqualTo(200);
        assertThat(json.readTree(submitted.body()).path("data").path("settlement").path("total").asInt()).isEqualTo(800000);
        var customer = request("GET", "/customer/bookings/" + booking.getId(), ownerToken, null);
        assertThat(customer.statusCode()).as(customer.body()).isEqualTo(200);
        assertThat(customer.body()).doesNotContain("800000", "private-vet", "private-stable", "internal-toll", "\"settlement\"");
        assertThat(request("GET", path, ownerToken, null).statusCode()).isEqualTo(403);
        assertThat(request("POST", path + "/issue", managerToken, "{\"payers\":{\"vet\":\"customer\"}}").statusCode()).isEqualTo(400);
        var approved = request("POST", path + "/issue", managerToken, "{}");
        assertThat(approved.statusCode()).as(approved.body()).isEqualTo(200);
        assertThat(json.readTree(approved.body()).path("data").path("totalCompanyCost").asInt()).isEqualTo(800000);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo("completed");
        assertThat(vehicles.findById(vehicle.getId()).orElseThrow().getStatus()).isEqualTo("ON_TRIP");
        assertThat(payments.findByBookingID_IdOrderByPaymentDateDesc(booking.getId())).isEmpty();
        String ratingPath = "/customer/bookings/" + booking.getId() + "/rating";
        assertThat(request("POST", ratingPath, otherToken, "{\"stars\":5}").statusCode()).isEqualTo(403);
        var rating = request("POST", ratingPath, ownerToken, "{\"stars\":5,\"comment\":\"Good\"}");
        assertThat(rating.statusCode()).as(rating.body()).isEqualTo(200);
        assertThat(rating.body()).doesNotContain("800000", "private-vet", "private-stable", "internal-toll", "\"settlement\"");
    }

    @Test
    void customerCannotCreateAPaymentOrUseTheRetiredSettlementEndpoint() throws Exception {
        var payment = request("POST", "/payments/vnpay/create", ownerToken,
                "{\"bookingId\":\"" + booking.getId() + "\",\"paymentType\":\"SETTLEMENT\"}");
        assertThat(payment.statusCode()).as(payment.body()).isEqualTo(400);
        assertThat(payments.findByBookingID_IdOrderByPaymentDateDesc(booking.getId())).isEmpty();
        assertThat(request("POST", "/settlements/" + booking.getId() + "/pay-and-complete", ownerToken, "{}").statusCode()).isBetween(400, 499);
    }
}
