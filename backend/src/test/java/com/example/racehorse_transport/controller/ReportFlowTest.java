package com.example.racehorse_transport.controller;

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// B6: báo cáo chuyến dựng từ trip_stop, horse_health_log, incident, cost_item, booking_rating
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class ReportFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private LocationRepository locations;
    @Autowired private EntityManager entityManager;

    private String manager;
    private String coordinator;
    private long trip;
    private Instant day;

    @BeforeEach
    void setUp() throws Exception {
        Staff coordinatorStaff = testData.staff(testData.unique("report.coordinator"), "COORDINATOR");
        coordinator = token(coordinatorStaff, "COORDINATOR");
        manager = token(testData.staff(testData.unique("report.manager"), "MANAGER"), "MANAGER");
        long booking = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DIRECT-001'", Long.class);
        long pickup = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'PICKUP'", Long.class, booking);
        long delivery = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'DELIVERY'", Long.class, booking);
        long line = jdbc.queryForObject("SELECT id FROM booking_horse WHERE booking_id = ?", Long.class, booking);
        long hcm = locations.findByCode("HUB-HCM").orElseThrow().getId();
        day = jdbc.queryForObject("SELECT requested_pickup_date FROM booking WHERE id = ?", java.sql.Date.class, booking)
                .toLocalDate().atTime(1, 0).toInstant(ZoneOffset.UTC);
        String created = mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripType\":\"DIRECT\",\"vehicleId\":%d,\"stops\":[%s,%s,%s,%s]}".formatted(testData.vehicle(2, 0).getId(),
                        stop("locationId", hcm, "HUB", 0), stop("bookingAddressId", pickup, "PICKUP", 60),
                        stop("bookingAddressId", delivery, "DELIVERY", 240), stop("locationId", hcm, "HUB", 480))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        trip = ((Number) JsonPath.read(created, "$.data.id")).longValue();
        List<Number> stops = JsonPath.read(created, "$.data.stops[*].id");
        mvc.perform(post("/vehicle-trips/{id}/horse-legs", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingHorseId\":%d,\"boardTripStopId\":%d,\"alightTripStopId\":%d}".formatted(line, stops.get(1).longValue(), stops.get(2).longValue())))
                .andExpect(status().isCreated());

        // Lượt xe đã chạy xong: tới điểm đón đúng giờ, tới điểm giao trễ 45 phút; có một sự cố, hai khoản chi, khách chấm 4 sao
        entityManager.flush();
        jdbc.update("UPDATE vehicle_trip SET status = 'COMPLETED', actual_start_at = planned_start_at, actual_end_at = planned_end_at WHERE id = ?", trip);
        jdbc.update("UPDATE trip_stop SET actual_arrival_at = planned_arrival_at + interval '10 minutes' WHERE id = ?", stops.get(1).longValue());
        jdbc.update("UPDATE trip_stop SET actual_arrival_at = planned_arrival_at + interval '45 minutes' WHERE id = ?", stops.get(2).longValue());
        jdbc.update("UPDATE horse_leg SET status = 'COMPLETED' WHERE vehicle_trip_id = ?", trip);
        long incident = jdbc.queryForObject("""
                INSERT INTO incident (booking_id, vehicle_trip_id, incident_type, severity, cause_type, title, description, reported_by, status)
                VALUES (?, ?, 'DELAY', 'LOW', 'FORCE_MAJEURE', 'Kẹt xe cao tốc', 'Tai nạn phía trước', ?, 'CLOSED') RETURNING id""",
                Long.class, booking, trip, coordinatorStaff.getUserId());
        jdbc.update("""
                INSERT INTO cost_item (category, description, amount, currency_code, payer_type, vehicle_trip_id, incurred_at, recorded_by, approved_by, approved_at)
                VALUES ('TOLL', 'Phí cao tốc', 300000, 'VND', 'COMPANY', ?, now(), ?, ?, now())""", trip, coordinatorStaff.getUserId(), coordinatorStaff.getUserId());
        jdbc.update("""
                INSERT INTO cost_item (category, description, amount, currency_code, payer_type, booking_id, incident_id, incurred_at, recorded_by)
                VALUES ('VET', 'Khám thú y', 1500000, 'VND', 'CUSTOMER', ?, ?, now(), ?)""", booking, incident, coordinatorStaff.getUserId());
        jdbc.update("INSERT INTO booking_rating (booking_id, score, comment) VALUES (?, 4, 'Giao hơi trễ')", booking);
        entityManager.clear();
    }

    @Test
    void tripListAndDetailCombineStopsIncidentsCostsAndRatings() throws Exception {
        mvc.perform(get("/reports/trips").param("from", day.minusSeconds(3600).toString()).param("to", day.plusSeconds(86400).toString())
                        .header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)].stopCount".formatted(trip)).value(4))
                .andExpect(jsonPath("$.data[?(@.id == %d)].arrivedStopCount".formatted(trip)).value(2))
                .andExpect(jsonPath("$.data[?(@.id == %d)].lateStopCount".formatted(trip)).value(1))
                .andExpect(jsonPath("$.data[?(@.id == %d)].horseCount".formatted(trip)).value(1))
                .andExpect(jsonPath("$.data[?(@.id == %d)].incidentCount".formatted(trip)).value(1))
                .andExpect(jsonPath("$.data[?(@.id == %d)].costTotal".formatted(trip)).value(1800000.0))
                .andExpect(jsonPath("$.data[?(@.id == %d)].averageRating".formatted(trip)).value(4.0));

        mvc.perform(get("/reports/trips/{id}", trip).header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stops[1].late").value(false))
                .andExpect(jsonPath("$.data.stops[2].late").value(true))
                .andExpect(jsonPath("$.data.incidents[0].title").value("Kẹt xe cao tốc"))
                .andExpect(jsonPath("$.data.costs.length()").value(2))
                .andExpect(jsonPath("$.data.costs[?(@.category == 'TOLL')].approved").value(true))
                .andExpect(jsonPath("$.data.costs[?(@.category == 'VET')].approved").value(false))
                .andExpect(jsonPath("$.data.ratings[0].bookingCode").value("DEV-DIRECT-001"))
                .andExpect(jsonPath("$.data.ratings[0].score").value(4));
    }

    @Test
    void summaryCountsTheRangeAndOnlyManagersSeeReports() throws Exception {
        summary(manager).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripCount").value(1))
                .andExpect(jsonPath("$.data.completedTripCount").value(1))
                .andExpect(jsonPath("$.data.horsesDelivered").value(1))
                .andExpect(jsonPath("$.data.onTimeRate").value(closeTo(0.5, 0.0001)))
                .andExpect(jsonPath("$.data.incidentCount").value(1))
                .andExpect(jsonPath("$.data.openIncidentCount").value(0))
                .andExpect(jsonPath("$.data.incidentsByType[0].key").value("DELAY"))
                .andExpect(jsonPath("$.data.costTotal").value(1800000.0))
                .andExpect(jsonPath("$.data.costsByPayer[?(@.key == 'CUSTOMER')].amount").value(1500000.0))
                .andExpect(jsonPath("$.data.ratingCount").value(1));
        summary(coordinator).andExpect(status().isForbidden());
    }

    private ResultActions summary(String token) throws Exception {
        return mvc.perform(get("/reports/operations").param("from", day.minusSeconds(3600).toString()).param("to", day.plusSeconds(86400).toString())
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private String stop(String key, long id, String type, int minutes) {
        String at = day.plusSeconds(60L * minutes).toString();
        String leave = day.plusSeconds(60L * (minutes + 30)).toString();
        return "{\"%s\":%d,\"stopType\":\"%s\",\"plannedArrivalAt\":\"%s\",\"plannedDepartureAt\":\"%s\"}".formatted(key, id, type, at, leave);
    }

    private String token(Staff staff, String role) {
        return "Bearer " + jwtUtils.generateToken(staff.getUser().getUsername(), List.of(role), 0);
    }
}
