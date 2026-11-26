package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class IncidentPlanExecutionFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwt;
    @Autowired private EntityManager em;

    private String coordinator;
    private String manager;
    private String escort;
    private String driver;
    private long booking;
    private long delivery;
    private long hub;
    private long trip;
    private long horse;
    private long otherHorse;
    private long leg;
    private long otherLeg;
    private List<Long> stops;
    private Instant start;

    @BeforeEach
    void setUp() throws Exception {
        coordinator = staffToken("COORDINATOR");
        manager = staffToken("MANAGER");
        Staff escortStaff = testData.staff(testData.unique("plan-execution.escort"), "ESCORT");
        escort = token(escortStaff, "ESCORT");
        Staff driverStaff = testData.staff(testData.unique("plan-execution.driver"), "DRIVER");
        driver = token(driverStaff, "DRIVER");
        booking = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DIRECT-001'", Long.class);
        start = jdbc.queryForObject("SELECT requested_pickup_date FROM booking WHERE id = ?", java.sql.Date.class, booking)
                .toLocalDate().atTime(1, 0).toInstant(ZoneOffset.UTC);
        long pickup = address("PICKUP");
        delivery = address("DELIVERY");
        hub = jdbc.queryForObject("SELECT id FROM location WHERE code = 'HUB-HCM'", Long.class);
        horse = jdbc.queryForObject("SELECT id FROM booking_horse WHERE booking_id = ?", Long.class, booking);
        otherHorse = jdbc.queryForObject("""
                INSERT INTO booking_horse (booking_id, horse_id, stall_type_code, status)
                SELECT ?, id, 'STANDARD', 'ACTIVE' FROM horse WHERE microchip = '985000000100002'
                RETURNING id
                """, Long.class, booking);
        String created = createTrip("DIRECT", List.of(
                stop("locationId", hub, "HUB", start, 0), stop("bookingAddressId", pickup, "PICKUP", start, 60),
                stop("locationId", hub, "HUB", start, 90), stop("bookingAddressId", delivery, "DELIVERY", start, 150),
                stop("locationId", hub, "HUB", start, 210)));
        trip = ((Number) JsonPath.read(created, "$.data.id")).longValue();
        stops = ids(created, "$.data.stops[*].id");
        postJson(coordinator, "/vehicle-trips/" + trip + "/crew-assignments",
                "{\"staffId\":%d,\"crewRole\":\"ESCORT\"}".formatted(escortStaff.getUserId())).andExpect(status().isCreated());
        postJson(coordinator, "/vehicle-trips/" + trip + "/crew-assignments",
                "{\"staffId\":%d,\"crewRole\":\"DRIVER\"}".formatted(driverStaff.getUserId())).andExpect(status().isCreated());
        leg = assign(trip, horse, stops.get(1), stops.get(3));
        otherLeg = assign(trip, otherHorse, stops.get(1), stops.get(3));
        em.flush();
        jdbc.update("UPDATE booking SET status = 'IN_OPERATION' WHERE id = ?", booking);
        jdbc.update("UPDATE vehicle_trip SET status = 'IN_PROGRESS', actual_start_at = now() - interval '1 hour' WHERE id = ?", trip);
        jdbc.update("UPDATE horse_leg SET status = 'IN_PROGRESS', actual_start_at = now() - interval '40 minutes' WHERE id IN (?, ?)", leg, otherLeg);
        atStop(2);
    }

    @Test
    void vetResultUsesTheLatestApprovedPlanAndLogsEveryHorseOnBoardOnce() throws Exception {
        long incident = report(null);
        long vet = party("Phòng khám thú y ngựa (mẫu)");
        long first = propose(incident, "VET_TREATMENT", vet);
        vetResult(incident, "Đã khám", coordinator).andExpect(status().isConflict());
        approve(first);
        long latest = propose(incident, "VET_TREATMENT", vet);
        vetResult(incident, "Đã khám", escort).andExpect(status().isConflict());
        approve(latest);
        postJson(escort, "/incidents/" + incident + "/vet-result",
                "{\"note\":\"Đã khám\",\"incidentPlanId\":%d}".formatted(first)).andExpect(status().isConflict());
        vetResult(incident, "   ", escort).andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.note").exists());
        vetResult(incident, "Đã khám", staffToken("ESCORT")).andExpect(status().isForbidden());
        vetResult(incident, "Đã khám", staffToken("DRIVER")).andExpect(status().isForbidden());
        atStop(0);
        vetResult(incident, "Đã khám", escort).andExpect(status().isConflict());
        atStop(3);
        // Xe đã rời điểm gần nhất. Kết quả vẫn gắn với điểm vừa tới.
        jdbc.update("UPDATE trip_stop SET actual_departure_at = now() WHERE id = ?", stops.get(2));
        jdbc.update("""
                INSERT INTO horse_health_log (booking_horse_id, recorded_by, recorded_at, trip_stop_id, health_status)
                SELECT ?, staff_id, now() - interval '10 minutes', ?, 'CRITICAL' FROM crew_assignment
                WHERE vehicle_trip_id = ? AND crew_role = 'ESCORT'
                """, horse, stops.get(1), trip);
        vetResult(incident, "  Đã khám và theo dõi  ", escort).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plans[1].status").value("DONE"));
        vetResult(incident, "Ghi lại", coordinator).andExpect(status().isConflict());
        assertThat(jdbc.queryForList("""
                SELECT booking_horse_id FROM horse_health_log WHERE trip_stop_id = ? AND
                    note = 'Thú y: Phòng khám thú y ngựa (mẫu)' || chr(10) || 'Đã khám và theo dõi'
                """, Long.class, stops.get(2))).containsExactlyInAnyOrder(horse, otherHorse);
        assertThat(jdbc.queryForObject("SELECT health_status FROM horse_health_log WHERE booking_horse_id = ? AND trip_stop_id = ?",
                String.class, horse, stops.get(2))).isEqualTo("CRITICAL");
    }

    @Test
    void coordinatorRecordsVetResultForTheAffectedHorse() throws Exception {
        long incident = report(leg);
        approve(propose(incident, "VET_TREATMENT", party("Phòng khám thú y ngựa (mẫu)")));
        vetResult(incident, "Đã khám", coordinator).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plans[0].status").value("DONE"));
        assertThat(jdbc.queryForList("SELECT booking_horse_id FROM horse_health_log WHERE trip_stop_id = ?", Long.class, stops.get(1)))
                .containsExactly(horse);
    }

    @Test
    void stablePlanRequiresItsPartnerAndAChipScanAtANetworkStop() throws Exception {
        long incident = report(leg);
        long stable = party("Chuồng gửi ngựa Đà Nẵng (mẫu)");
        long wrongPartner = party("Chuồng gửi ngựa Tuy Hòa (mẫu)");
        long plan = propose(incident, "STABLE_NEARBY", stable);
        atStop(3);
        openStable("STAY_IN", stops.get(2), stable, plan).andExpect(status().isConflict());
        openStable("STAY_IN", stops.get(2), stable, null).andExpect(status().isBadRequest());
        approve(plan);
        atStop(2);
        openStable("STAY_IN", stops.get(1), stable, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không gửi chuồng tại địa chỉ khách được. Chọn đổi xe hoặc đưa về hub."));
        atStop(3);
        openStable("STAY_IN", stops.get(2), wrongPartner, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chỉ gửi cho đối tác chuồng của phương án đã duyệt."));
        long in = handoverId(openStable("STAY_IN", stops.get(2), stable, plan).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.handovers[0].horses.length()").value(1)));
        scan(in, otherLeg, "985000000100002").andExpect(status().isConflict());
        act(escort, "/handovers/" + in + "/complete").andExpect(status().isConflict());
        scan(in, leg, "985000000100001").andExpect(status().isOk());
        act(escort, "/handovers/" + in + "/complete").andExpect(status().isOk());
        mvc.perform(get("/incidents/{id}", incident).header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.plans[0].status").value("DONE"));
        openStable("STAY_IN", stops.get(2), stable, plan).andExpect(status().isConflict());
        act(escort, "/handovers/" + in + "/complete").andExpect(status().isConflict());
        assertThat(jdbc.queryForList("""
                SELECT booking_horse_id FROM horse_stay WHERE stay_type = 'OVERNIGHT' AND location_id = ?
                    AND operator_party_id = ? AND actual_end_at IS NULL
                """, Long.class, hub, stable)).containsExactly(horse);
        act(driver, "/trip-stops/" + stops.get(2) + "/depart").andExpect(status().isConflict());

        long out = handoverId(openStable("STAY_OUT", stops.get(2), null, null).andExpect(status().isCreated()));
        scan(out, leg, "985000000100001").andExpect(status().isOk());
        act(escort, "/handovers/" + out + "/complete").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT from_party_id FROM handover WHERE id = ?", Long.class, out)).isEqualTo(stable);
        assertThat(jdbc.queryForObject("SELECT actual_end_at IS NOT NULL FROM horse_stay WHERE booking_horse_id = ? AND stay_type = 'OVERNIGHT'",
                Boolean.class, horse)).isTrue();
    }

    @Test
    void replacingAStablePlanClosesItsDraftAndTheNewTripPlanRequiresEveryHorse() throws Exception {
        long incident = report(null);
        long stable = party("Chuồng gửi ngựa Đà Nẵng (mẫu)");
        long oldPlan = propose(incident, "STABLE_NEARBY", stable);
        approve(oldPlan);
        atStop(3);
        long previous = handoverId(openStable("STAY_IN", stops.get(2), stable, oldPlan).andExpect(status().isCreated()));
        scan(previous, leg, "985000000100001").andExpect(status().isOk());
        long newPlan = propose(incident, "STABLE_NEARBY", stable);
        act(escort, "/handovers/" + previous + "/complete").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM handover WHERE id = ?", String.class, previous)).isEqualTo("FAILED");
        approve(newPlan);
        openStable("STAY_IN", stops.get(2), stable, oldPlan).andExpect(status().isConflict());
        long current = handoverId(openStable("STAY_IN", stops.get(2), stable, null).andExpect(status().isCreated()));
        scan(current, leg, "985000000100001").andExpect(status().isOk());
        act(escort, "/handovers/" + current + "/complete").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Quét đúng chip của mọi con thuộc phương án trước khi gửi chuồng."));
        act(coordinator, "/incidents/" + incident + "/resolve").andExpect(status().isConflict());
        scan(current, otherLeg, "985000000100002").andExpect(status().isOk());
        act(escort, "/handovers/" + current + "/complete").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM incident_plan WHERE id = ?", String.class, newPlan)).isEqualTo("DONE");
    }

    @Test
    void cancellationIsScopedToTheHorseAndReleasesItsLaterPlannedLeg() throws Exception {
        List<Long> later = laterLegs();
        long incident = report(leg);
        long plan = propose(incident, "CANCEL", null);
        String path = "/incidents/" + incident + "/cancel-legs";
        act(coordinator, path).andExpect(status().isConflict());
        approve(plan);
        act(escort, path).andExpect(status().isForbidden());
        atStop(5);
        act(coordinator, path).andExpect(status().isConflict());
        atStop(3);
        postJson(coordinator, path, "{\"incidentPlanId\":%d}".formatted(plan)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plans[0].status").value("DONE"));
        act(coordinator, path).andExpect(status().isConflict());
        em.flush();
        assertThat(legStatus(leg)).isEqualTo("FAILED");
        assertThat(legStatus(otherLeg)).isEqualTo("IN_PROGRESS");
        assertThat(legStatus(later.getFirst())).isEqualTo("CANCELLED");
        assertThat(legStatus(later.getLast())).isEqualTo("PLANNED");
        assertThat(jdbc.queryForObject("SELECT stall_position IS NULL FROM horse_leg WHERE id = ?", Boolean.class, later.getFirst())).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log WHERE action = 'CANCEL_INCIDENT_LEGS' AND target_id = ?
                    AND (new_data -> 'cancelledLegIds' ->> 0)::bigint = ?
                """, Long.class, incident, later.getFirst())).isEqualTo(1L);
    }

    @Test
    void cancelledTripHorsesAreHandedIntoTheHubForFree() throws Exception {
        List<Long> later = laterLegs();
        long incident = report(null);
        approve(propose(incident, "CANCEL", null));
        atStop(4);
        long deliveryHandover = handoverId(postJson(escort, "/trip-stops/" + stops.get(3) + "/handovers",
                "{\"handoverType\":\"DELIVERY\"}").andExpect(status().isCreated()));
        act(coordinator, "/incidents/" + incident + "/cancel-legs").andExpect(status().isOk());
        act(coordinator, "/incidents/" + incident + "/resolve").andExpect(status().isOk());
        assertThat(legStatus(later.getFirst())).isEqualTo("CANCELLED");
        assertThat(legStatus(later.getLast())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM handover WHERE id = ?", String.class, deliveryHandover)).isEqualTo("FAILED");
        act(driver, "/trip-stops/" + stops.get(3) + "/depart").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(4) + "/arrive").andExpect(status().isOk());
        long in = handoverId(postJson(escort, "/trip-stops/" + stops.get(4) + "/handovers",
                "{\"handoverType\":\"HUB_IN\"}").andExpect(status().isCreated()));
        scan(in, leg, "985000000100001").andExpect(status().isOk());
        scan(in, otherLeg, "985000000100002").andExpect(status().isOk());
        act(escort, "/handovers/" + in + "/complete").andExpect(status().isOk());
        em.flush();
        assertThat(jdbc.queryForList("""
                SELECT booking_horse_id FROM horse_stay WHERE location_id = ? AND stay_type = 'ASSEMBLY'
                    AND booking_horse_id IN (?, ?) AND actual_end_at IS NULL
                """, Long.class, hub, horse, otherHorse)).containsExactlyInAnyOrder(horse, otherHorse);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cost_item WHERE booking_id = ?", Long.class, booking)).isZero();
    }

    private long assign(long tripId, long horseId, long board, long alight) throws Exception {
        postJson(coordinator, "/vehicle-trips/" + tripId + "/horse-legs",
                "{\"bookingHorseId\":%d,\"boardTripStopId\":%d,\"alightTripStopId\":%d}".formatted(horseId, board, alight))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM horse_leg WHERE vehicle_trip_id = ? AND booking_horse_id = ?", Long.class, tripId, horseId);
    }

    private List<Long> laterLegs() throws Exception {
        Instant tomorrow = start.plus(1, ChronoUnit.DAYS);
        String created = createTrip("TRANSFER", List.of(stop("locationId", hub, "HUB", tomorrow, 0),
                stop("bookingAddressId", delivery, "DELIVERY", tomorrow, 60), stop("locationId", hub, "HUB", tomorrow, 120)));
        long tripId = ((Number) JsonPath.read(created, "$.data.id")).longValue();
        List<Long> laterStops = ids(created, "$.data.stops[*].id");
        return List.of(assign(tripId, horse, laterStops.get(1), laterStops.get(2)),
                assign(tripId, otherHorse, laterStops.get(1), laterStops.get(2)));
    }

    private void atStop(int sequence) {
        em.flush();
        jdbc.update("""
                UPDATE trip_stop SET
                    actual_arrival_at = CASE WHEN sequence_number <= ? THEN now() - (6 - sequence_number) * interval '10 minutes' END,
                    actual_departure_at = CASE WHEN sequence_number < ? THEN now() - (6 - sequence_number) * interval '9 minutes' END
                WHERE vehicle_trip_id = ?
                """, sequence, sequence, trip);
        em.clear();
    }

    private long report(Long horseLegId) throws Exception {
        return id(postJson(escort, "/incidents", """
                {"vehicleTripId":%d,"horseLegId":%s,"incidentType":"OTHER","causeType":"COMPANY_FAULT",
                "title":"Sự cố cần xử lý","description":"Ghi nhận trên lượt"}
                """.formatted(trip, horseLegId)).andExpect(status().isCreated()));
    }

    private long propose(long incident, String type, Long handler) throws Exception {
        return ids(body(postJson(coordinator, "/incidents/" + incident + "/plans",
                "{\"resolutionType\":\"%s\",\"instructions\":\"Chỉ đạo\",\"handlerPartyId\":%s}".formatted(type, handler))
                .andExpect(status().isCreated())), "$.data.plans[*].id").getLast();
    }

    private void approve(long plan) throws Exception {
        act(manager, "/incident-plans/" + plan + "/approve").andExpect(status().isOk());
    }

    private ResultActions vetResult(long incident, String note, String actor) throws Exception {
        return postJson(actor, "/incidents/" + incident + "/vet-result", "{\"note\":\"%s\"}".formatted(note));
    }

    private ResultActions openStable(String type, long stop, Long party, Long plan) throws Exception {
        String path = "/trip-stops/" + stop + "/handovers" + (plan == null ? "" : "?incidentPlanId=" + plan);
        return postJson(escort, path, "{\"handoverType\":\"%s\",\"partyId\":%s}".formatted(type, party));
    }

    private ResultActions scan(long handover, long legId, String chip) throws Exception {
        return postJson(escort, "/handovers/" + handover + "/horses",
                "{\"horseLegId\":%d,\"microchip\":\"%s\"}".formatted(legId, chip));
    }

    private ResultActions postJson(String actor, String path, String content) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, actor).contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private ResultActions act(String actor, String path) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, actor));
    }

    private String createTrip(String type, List<String> stopsJson) throws Exception {
        return body(postJson(coordinator, "/vehicle-trips", "{\"tripType\":\"%s\",\"vehicleId\":%d,\"stops\":[%s]}"
                .formatted(type, testData.vehicle(3, 0).getId(), String.join(",", stopsJson))).andExpect(status().isCreated()));
    }

    private static String stop(String key, long id, String type, Instant base, int minutes) {
        Instant at = base.plus(minutes, ChronoUnit.MINUTES);
        return "{\"%s\":%d,\"stopType\":\"%s\",\"plannedArrivalAt\":\"%s\",\"plannedDepartureAt\":\"%s\"}"
                .formatted(key, id, type, at, at.plus(15, ChronoUnit.MINUTES));
    }

    private long address(String type) {
        return jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = ?", Long.class, booking, type);
    }

    private long party(String name) {
        return jdbc.queryForObject("SELECT id FROM party WHERE display_name = ?", Long.class, name);
    }

    private String legStatus(long legId) {
        return jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, legId);
    }

    private String staffToken(String role) {
        return token(testData.staff(testData.unique("plan-execution." + role.toLowerCase(java.util.Locale.ROOT)), role), role);
    }

    private String token(Staff staff, String role) {
        return "Bearer " + jwt.generateToken(staff.getUser().getUsername(), List.of(role));
    }

    private static long handoverId(ResultActions response) throws Exception {
        return ids(body(response), "$.data.handovers[*].id").getLast();
    }

    private static long id(ResultActions response) throws Exception {
        return ((Number) JsonPath.read(body(response), "$.data.id")).longValue();
    }

    private static String body(ResultActions response) throws Exception {
        return response.andReturn().getResponse().getContentAsString();
    }

    private static List<Long> ids(String body, String path) {
        List<Number> ids = JsonPath.read(body, path);
        return ids.stream().map(Number::longValue).toList();
    }
}
