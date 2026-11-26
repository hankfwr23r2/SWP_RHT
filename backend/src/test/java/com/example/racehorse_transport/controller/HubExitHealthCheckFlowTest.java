package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.scheduler.HealthCheckTaskScheduler;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class HubExitHealthCheckFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;
    @Autowired private Clock clock;
    @Autowired private HealthCheckTaskScheduler scheduler;

    private Staff coordinator;
    private Staff escort;
    private long horse;
    private long stay;
    private long hub;
    private long otherHub;
    private Instant dueAt;

    @BeforeEach
    void setUp() {
        coordinator = data.staff(data.unique("m5.coordinator"), "COORDINATOR");
        escort = data.staff(data.unique("m5.escort"), "ESCORT");
        hub = coordinator.getHomeHubLocation().getId();
        otherHub = jdbc.queryForObject("SELECT id FROM location WHERE code = 'HUB-DN'", Long.class);
        horse = jdbc.queryForObject("""
                SELECT bh.id FROM booking_horse bh JOIN booking b ON b.id = bh.booking_id WHERE b.code = 'DEV-DIRECT-001'
                """, Long.class);
        stay = jdbc.queryForObject("""
                INSERT INTO horse_stay (booking_horse_id, location_id, stay_type, planned_start_at, planned_end_at, actual_start_at)
                VALUES (?, ?, 'ASSEMBLY', now(), now() + interval '1 day', now()) RETURNING id
                """, Long.class, horse, hub);
        dueAt = clock.instant().truncatedTo(ChronoUnit.SECONDS).plus(4, ChronoUnit.HOURS);
    }

    @Test
    void schedulesDeliveryWithinConfiguredWindowWithCoordinatorFallbackAndSkipsTransfer() throws Exception {
        long delivery = plannedTrip("DELIVERY", hub);
        long transfer = plannedTrip("TRANSFER", hub);
        jdbc.update("""
                INSERT INTO crew_assignment (vehicle_trip_id, staff_id, crew_role, status) VALUES (?, ?, 'ESCORT', 'RELEASED')
                """, delivery, escort.getUserId());
        jdbc.update("UPDATE system_config SET config_value = '3' WHERE config_key = 'PRE_EXIT_CHECK_HOURS'");
        scheduler.run();
        assertThat(taskCount(delivery)).isZero();

        jdbc.update("UPDATE system_config SET config_value = '6' WHERE config_key = 'PRE_EXIT_CHECK_HOURS'");
        entityManager.clear();
        scheduler.run();
        scheduler.run();
        assertThat(taskCount(transfer)).isZero();
        assertThat(taskCount(delivery)).isEqualTo(1);
        assertThat(jdbc.queryForMap("""
                SELECT check_type, assigned_to, assigned_by, horse_stay_id FROM health_check_task WHERE vehicle_trip_id = ?
                """, delivery)).containsEntry("check_type", "BEFORE_DELIVERY")
                .containsEntry("assigned_to", coordinator.getUserId()).containsEntry("assigned_by", coordinator.getUserId())
                .containsEntry("horse_stay_id", null);

        mvc.perform(get("/horse-stays").param("locationId", Long.toString(hub)).header(HttpHeaders.AUTHORIZATION, token(coordinator, "COORDINATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].hubExitChecks[0].checkType").value("BEFORE_DELIVERY"))
                .andExpect(jsonPath("$.data[0].hubExitChecks[0].dueAt").value(dueAt.toString()))
                .andExpect(jsonPath("$.data[0].hubExitChecks[0].recordingHorseStayId").value(stay));

        mvc.perform(post("/health-logs").header(HttpHeaders.AUTHORIZATION, token(coordinator, "COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"bookingHorseId":%d,"horseStayId":%d,"healthStatus":"NORMAL","reportedBy":"Hộ tống","reportChannel":"Điện thoại"}
                        """.formatted(horse, stay))).andExpect(status().isCreated());
        entityManager.flush();
        scheduler.run();
        assertThat(jdbc.queryForObject("SELECT status FROM health_check_task WHERE vehicle_trip_id = ?", String.class, delivery))
                .isEqualTo("DONE");
        assertThat(taskCount(delivery)).isEqualTo(1);
    }

    @Test
    void doesNotScheduleBoardingForAHorseStayingAtAnotherHub() {
        long delivery = plannedTrip("DELIVERY", otherHub);
        scheduler.run();
        assertThat(taskCount(delivery)).isZero();
    }

    @Test
    void canReassignTheSameCheckWhenAnEscortJoinsTheTrip() throws Exception {
        long delivery = plannedTrip("DELIVERY", hub);
        scheduler.run();
        long task = jdbc.queryForObject("SELECT id FROM health_check_task WHERE vehicle_trip_id = ?", Long.class, delivery);
        jdbc.update("""
                INSERT INTO crew_assignment (vehicle_trip_id, staff_id, crew_role, status) VALUES (?, ?, 'ESCORT', 'ASSIGNED')
                """, delivery, escort.getUserId());

        mvc.perform(post("/health-check-tasks").header(HttpHeaders.AUTHORIZATION, token(coordinator, "COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"bookingHorseId":%d,"vehicleTripId":%d,"assignedTo":%d,"checkType":"BEFORE_DELIVERY","dueAt":"%s"}
                        """.formatted(horse, delivery, escort.getUserId(), dueAt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(task));
        scheduler.run();
        assertThat(taskCount(delivery)).isEqualTo(1);
        mvc.perform(get("/health-check-tasks/mine").header(HttpHeaders.AUTHORIZATION, token(escort, "ESCORT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].recordingHorseStayId").value(stay));
        mvc.perform(post("/health-logs").header(HttpHeaders.AUTHORIZATION, token(escort, "ESCORT"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"bookingHorseId":%d,"horseStayId":%d,"healthStatus":"NORMAL"}
                        """.formatted(horse, stay))).andExpect(status().isCreated());
        mvc.perform(get("/health-check-tasks/mine").header(HttpHeaders.AUTHORIZATION, token(escort, "ESCORT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("DONE"))
                .andExpect(jsonPath("$.data[0].recordingHorseStayId").doesNotExist());
    }

    private long plannedTrip(String type, long origin) {
        long vehicle = data.vehicle(4, 0).getId();
        long end = type.equals("TRANSFER") ? otherHub : origin;
        Timestamp departure = Timestamp.from(dueAt);
        Timestamp arrival = Timestamp.from(dueAt.plus(1, ChronoUnit.HOURS));
        long trip = jdbc.queryForObject("""
                INSERT INTO vehicle_trip (code, trip_type, vehicle_id, start_location_id, end_location_id,
                    planned_start_at, planned_end_at, status, coordinator_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PLANNED', ?) RETURNING id
                """, Long.class, data.unique("M5"), type, vehicle, origin, end, departure, arrival, coordinator.getUserId());
        long board = hubStop(trip, 1, origin, departure);
        Long address = type.equals("DELIVERY") ? jdbc.queryForObject("""
                SELECT ba.id FROM booking_horse bh JOIN booking_address ba ON ba.booking_id = bh.booking_id
                WHERE bh.id = ? AND ba.address_type = 'DELIVERY'
                """, Long.class, horse) : null;
        long alight = address == null ? hubStop(trip, 2, end, arrival) : jdbc.queryForObject("""
                INSERT INTO trip_stop (vehicle_trip_id, sequence_number, booking_address_id, stop_type, planned_arrival_at, planned_departure_at)
                VALUES (?, 2, ?, 'DELIVERY', ?, ?) RETURNING id
                """, Long.class, trip, address, arrival, arrival);
        jdbc.update("""
                INSERT INTO horse_leg (booking_horse_id, sequence_number, leg_type, from_location_id, to_location_id,
                    to_booking_address_id, vehicle_trip_id, board_trip_stop_id, alight_trip_stop_id, planned_start_at, planned_end_at, status)
                SELECT ?, coalesce(max(sequence_number), 0) + 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PLANNED'
                FROM horse_leg WHERE booking_horse_id = ?
                """, horse, type, origin, address == null ? end : null, address, trip, board, alight, departure, arrival, horse);
        return trip;
    }

    private long hubStop(long trip, int sequence, long location, Timestamp at) {
        return jdbc.queryForObject("""
                INSERT INTO trip_stop (vehicle_trip_id, sequence_number, location_id, stop_type, planned_arrival_at, planned_departure_at)
                VALUES (?, ?, ?, 'HUB', ?, ?) RETURNING id
                """, Long.class, trip, sequence, location, at, at);
    }

    private int taskCount(long trip) {
        return jdbc.queryForObject("SELECT count(*) FROM health_check_task WHERE vehicle_trip_id = ?", Integer.class, trip);
    }

    private String token(Staff actor, String role) {
        return "Bearer " + jwt.generateToken(actor.getUser().getUsername(), List.of(role));
    }
}
