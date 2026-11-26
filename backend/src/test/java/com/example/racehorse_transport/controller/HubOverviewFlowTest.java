package com.example.racehorse_transport.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class HubOverviewFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entities;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;
    @Autowired private Clock clock;

    @Test
    void managersAndAdminsCanReadTheOverview() throws Exception {
        overview(token("MANAGER")).andExpect(status().isOk());
        overview(token("ADMIN")).andExpect(status().isOk());
        overview(token("COORDINATOR")).andExpect(status().isForbidden());
        mvc.perform(get("/hubs/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void showsActiveDomesticAndForeignHubsWithCurrentOperations() throws Exception {
        String manager = token("MANAGER");
        long reporter = data.staff(data.unique("m8.reporter"), "COORDINATOR").getUserId();
        long home = jdbc.queryForObject("SELECT id FROM location WHERE code = 'HUB-HCM'", Long.class);
        long foreign = hub("M8-PNH", "KH", "HUB", "ACTIVE");
        hub("M8-CLOSED", "VN", "HUB", "INACTIVE");
        hub("M8-REST", "VN", "REST_STOP", "ACTIVE");
        hub("M8-EMPTY", "VN", "HUB", "ACTIVE");

        long horse = jdbc.queryForObject("""
                SELECT h.id FROM booking_horse h JOIN booking b ON b.id = h.booking_id WHERE b.code = 'DEV-DIRECT-001'
                """, Long.class);
        String horseName = jdbc.queryForObject("""
                SELECT h.name FROM horse h JOIN booking_horse line ON line.horse_id = h.id WHERE line.id = ?
                """, String.class, horse);
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant entered = now.minus(2, ChronoUnit.HOURS);
        stay(horse, foreign, stall(foreign, "A1", true), entered, null);
        long free = stall(foreign, "A2", true);
        stay(horse, foreign, free, entered.minus(2, ChronoUnit.DAYS), entered.minus(1, ChronoUnit.DAYS));
        stay(horse, foreign, free, null, null);
        stall(foreign, "A3", false);
        staffAt(foreign, "ACTIVE");
        staffAt(foreign, "ON_LEAVE");

        long neverRun = data.vehicle(2, 0).getId();
        trip(neverRun, home, foreign, now.plus(3, ChronoUnit.DAYS), "PLANNED");
        long idle = data.vehicle(2, 0).getId();
        trip(idle, home, home, now.minus(5, ChronoUnit.DAYS), "COMPLETED");
        trip(idle, home, foreign, now.minus(3, ChronoUnit.DAYS), "COMPLETED");
        trip(idle, foreign, home, now.plus(2, ChronoUnit.DAYS), "PLANNED");
        long maintenance = data.vehicle(2, 0).getId();
        trip(maintenance, home, foreign, now.minus(2, ChronoUnit.DAYS), "COMPLETED");
        jdbc.update("UPDATE vehicle SET status = 'MAINTENANCE' WHERE id = ?", maintenance);

        long running = data.vehicle(2, 0).getId();
        long runningTrip = trip(running, foreign, home, entered, "IN_PROGRESS");
        long board = stop(runningTrip, foreign, 1, entered, true);
        long alight = stop(runningTrip, home, 2, now.plus(1, ChronoUnit.HOURS), false);
        long runningLeg = leg(horse, 90, runningTrip, board, alight, "PLANNED");
        long returning = data.vehicle(2, 0).getId();
        long returningTrip = trip(returning, home, foreign, entered, "IN_PROGRESS");
        stop(returningTrip, home, 1, entered, true);
        stop(returningTrip, foreign, 2, now.plus(1, ChronoUnit.HOURS), false);
        long arrivedTrip = trip(data.vehicle(2, 0).getId(), home, foreign, entered, "IN_PROGRESS");
        stop(arrivedTrip, home, 1, entered, true);
        stop(arrivedTrip, foreign, 2, now.minus(1, ChronoUnit.HOURS), true);

        long pastTrip = trip(data.vehicle(2, 0).getId(), home, home, now.minus(1, ChronoUnit.DAYS), "COMPLETED");
        long pastBoard = stop(pastTrip, home, 1, now.minus(1, ChronoUnit.DAYS), true);
        long pastAlight = stop(pastTrip, home, 2, now.minus(22, ChronoUnit.HOURS), true);
        long pastLeg = leg(horse, 89, pastTrip, pastBoard, pastAlight, "COMPLETED");
        incident("Khớp cả lượt và ngựa", reporter, runningTrip, runningLeg, "REPORTED");
        incident("Sự cố của ngựa đang ở hub", reporter, null, pastLeg, "REPORTED");
        incident("Sự cố lượt tới hub", reporter, returningTrip, null, "RESOLVED");
        incident("Sự cố đã đóng", reporter, runningTrip, null, "CLOSED");

        long inbound = route("M8-TO-PNH", home, foreign);
        long outbound = route("M8-FROM-PNH", foreign, home);
        Instant revised = now.plus(1, ChronoUnit.DAYS);
        departure(inbound, now.minus(1, ChronoUnit.DAYS), revised, "OPEN");
        departure(inbound, now.plus(8, ChronoUnit.DAYS), null, "OPEN");
        departure(inbound, now.minus(2, ChronoUnit.DAYS), null, "COMPLETED");
        departure(outbound, now.plus(6, ChronoUnit.DAYS), null, "CANCELLED");
        entities.clear();

        String pnh = "$.data[?(@.code == 'M8-PNH')]";
        String hcm = "$.data[?(@.code == 'HUB-HCM')]";
        overview(manager).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].headquarters").value(true))
                .andExpect(jsonPath("$.data[*].code", not(hasItem("M8-CLOSED"))))
                .andExpect(jsonPath("$.data[*].code", not(hasItem("M8-REST"))))
                .andExpect(jsonPath(pnh + ".countryCode").value("KH"))
                .andExpect(jsonPath(pnh + ".headquarters").value(false))
                .andExpect(jsonPath(pnh + ".horseCount").value(1))
                .andExpect(jsonPath(pnh + ".horses[*].horseName", contains(horseName)))
                .andExpect(jsonPath(pnh + ".horses[*].bookingCode", contains("DEV-DIRECT-001")))
                .andExpect(jsonPath(pnh + ".horses[*].stayType", contains("ASSEMBLY")))
                .andExpect(jsonPath(pnh + ".horses[*].actualStartAt", contains(entered.toString())))
                .andExpect(jsonPath(pnh + ".stalls.total").value(2))
                .andExpect(jsonPath(pnh + ".stalls.available").value(1))
                .andExpect(jsonPath("$.data[?(@.code == 'M8-EMPTY')].stalls.total").value(0))
                .andExpect(jsonPath(pnh + ".staff[0].roleCode").value("DRIVER"))
                .andExpect(jsonPath(pnh + ".staff[0].count").value(1))
                .andExpect(jsonPath(pnh + ".fleet.idle.count").value(1))
                .andExpect(jsonPath(pnh + ".fleet.idle.vehicles[0].id").value((int) idle))
                .andExpect(jsonPath(hcm + ".fleet.idle.vehicles[*].id", hasItem((int) neverRun)))
                .andExpect(jsonPath(pnh + ".fleet.running.vehicles[*].id", contains((int) running)))
                .andExpect(jsonPath(hcm + ".fleet.returning.vehicles[*].id", not(hasItem((int) running))))
                .andExpect(jsonPath(pnh + ".fleet.returning.vehicles[*].id", contains((int) returning)))
                .andExpect(jsonPath(pnh + ".fleet.maintenance.vehicles[*].id", contains((int) maintenance)))
                .andExpect(jsonPath(pnh + ".arrivals[*].routeCode", contains("M8-TO-PNH")))
                .andExpect(jsonPath(pnh + ".arrivals[*].departureAt", contains(revised.toString())))
                .andExpect(jsonPath(pnh + ".departures[*].status", contains("CANCELLED")))
                .andExpect(jsonPath(pnh + ".openIncidentCount").value(3))
                .andExpect(jsonPath(pnh + ".incidents[*].title", containsInAnyOrder(
                        "Khớp cả lượt và ngựa", "Sự cố của ngựa đang ở hub", "Sự cố lượt tới hub")));
    }

    private long hub(String code, String country, String type, String status) {
        return jdbc.queryForObject("""
                INSERT INTO location (code, name, country_code, location_type, address, status)
                VALUES (?, ?, ?, ?, 'Địa chỉ test', ?) RETURNING id
                """, Long.class, code, code, country, type, status);
    }

    private long stall(long hub, String code, boolean active) {
        return jdbc.queryForObject("""
                INSERT INTO hub_stall (location_id, code, stall_kind, is_active) VALUES (?, ?, 'STANDARD', ?) RETURNING id
                """, Long.class, hub, code, active);
    }

    private void stay(long horse, long hub, long stall, Instant start, Instant end) {
        jdbc.update("""
                INSERT INTO horse_stay (booking_horse_id, location_id, hub_stall_id, stay_type,
                                        planned_start_at, planned_end_at, actual_start_at, actual_end_at)
                VALUES (?, ?, ?, 'ASSEMBLY', now() - interval '3 days', now() + interval '1 day', ?, ?)
                """, horse, hub, stall, timestamp(start), timestamp(end));
    }

    private void staffAt(long hub, String status) {
        long id = data.staff(data.unique("m8.driver"), "DRIVER").getUserId();
        jdbc.update("UPDATE staff SET home_hub_location_id = ?, employment_status = ? WHERE user_id = ?", hub, status, id);
    }

    private long trip(long vehicle, long from, long to, Instant start, String status) {
        Instant end = start.plus(3, ChronoUnit.HOURS);
        return jdbc.queryForObject("""
                INSERT INTO vehicle_trip (code, vehicle_id, start_location_id, end_location_id, trip_type, status,
                                          planned_start_at, planned_end_at, actual_start_at, actual_end_at)
                VALUES (?, ?, ?, ?, 'TRANSFER', ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, data.unique("M8T"), vehicle, from, to, status, timestamp(start), timestamp(end),
                "PLANNED".equals(status) ? null : timestamp(start), "COMPLETED".equals(status) ? timestamp(end) : null);
    }

    private long stop(long trip, long location, int sequence, Instant at, boolean arrived) {
        return jdbc.queryForObject("""
                INSERT INTO trip_stop (vehicle_trip_id, location_id, sequence_number, stop_type,
                                       planned_arrival_at, planned_departure_at, actual_arrival_at, actual_departure_at)
                VALUES (?, ?, ?, 'HUB', ?, ?, ?, ?) RETURNING id
                """, Long.class, trip, location, sequence, timestamp(at), timestamp(at),
                arrived ? timestamp(at) : null, arrived && sequence == 1 ? timestamp(at) : null);
    }

    private long leg(long horse, int sequence, long trip, long board, long alight, String status) {
        return jdbc.queryForObject("""
                INSERT INTO horse_leg (booking_horse_id, sequence_number, leg_type, from_location_id, to_location_id,
                                       vehicle_trip_id, board_trip_stop_id, alight_trip_stop_id, planned_start_at, planned_end_at, status)
                SELECT ?, ?, 'TRANSFER', origin.location_id, destination.location_id, ?, origin.id, destination.id,
                       origin.planned_departure_at, destination.planned_arrival_at, ?
                FROM trip_stop origin, trip_stop destination WHERE origin.id = ? AND destination.id = ? RETURNING id
                """, Long.class, horse, sequence, trip, status, board, alight);
    }

    private void incident(String title, long reporter, Long trip, Long leg, String status) {
        jdbc.update("""
                INSERT INTO incident (vehicle_trip_id, horse_leg_id, incident_type, severity, cause_type, title,
                                      description, reported_by, status)
                VALUES (?, ?, 'HEALTH', 'LOW', 'HORSE_CONDITION', ?, ?, ?, ?)
                """, trip, leg, title, title, reporter, status);
    }

    private long route(String code, long origin, long destination) {
        return jdbc.queryForObject("""
                INSERT INTO route (code, name, origin_location_id, destination_location_id,
                                   distance_km, estimated_duration_minutes, status)
                VALUES (?, ?, ?, ?, 100, 120, 'ACTIVE') RETURNING id
                """, Long.class, code, code, origin, destination);
    }

    private void departure(long route, Instant planned, Instant revised, String status) {
        jdbc.update("""
                INSERT INTO departure (route_id, departure_date, planned_departure_at, revised_departure_at,
                                       booking_cutoff_at, stall_capacity, status)
                VALUES (?, ?, ?, ?, ?, 6, ?)
                """, route, LocalDate.ofInstant(planned, ZoneOffset.UTC), timestamp(planned), timestamp(revised),
                timestamp(planned.minus(1, ChronoUnit.DAYS)), status);
    }

    private ResultActions overview(String token) throws Exception {
        return mvc.perform(get("/hubs/overview").header(HttpHeaders.AUTHORIZATION, token));
    }

    private String token(String role) {
        var user = data.staff(data.unique("m8." + role.toLowerCase()), role).getUser();
        return "Bearer " + jwt.generateToken(user.getUsername(), List.of(role));
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
