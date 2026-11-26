package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.support.HandoverEvidenceFixtures;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.example.racehorse_transport.support.TripReturnPhotoFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
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

// B2: tổ xe chạy trọn một lượt chạy thẳng của đơn seed DEV-DIRECT-001: xuất phát, đón, quét chip, rời điểm đón, giao, kết thúc
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class TripExecutionFlowTest {

    private static final String INCIDENT_BLOCK_MESSAGE =
            "Lượt xe đang có sự cố nặng chưa có phương án được duyệt. Chờ quản lý duyệt hoặc điều phối mở khóa.";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private LocationRepository locations;
    @Autowired private EntityManager entityManager;
    // Lưu tệp lên Cloudinary là dịch vụ bên ngoài
    @org.springframework.test.context.bean.override.mockito.MockitoBean private com.cloudinary.Cloudinary cloudinary;

    private String coordinator;
    private long coordinatorId;
    private String driver;
    private String escort;
    private long trip;
    private List<Long> stops;
    private long leg;
    private long booking;
    private String chip;

    @BeforeEach
    void planDirectTrip() throws Exception {
        Staff coordinatorStaff = testData.staff(testData.unique("exec.coordinator"), "COORDINATOR");
        coordinator = token(coordinatorStaff, "COORDINATOR");
        coordinatorId = coordinatorStaff.getUserId();
        Staff driverStaff = testData.staff(testData.unique("exec.driver"), "DRIVER");
        Staff escortStaff = testData.staff(testData.unique("exec.escort"), "ESCORT");
        driver = token(driverStaff, "DRIVER");
        escort = token(escortStaff, "ESCORT");

        booking = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DIRECT-001'", Long.class);
        long pickup = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'PICKUP'", Long.class, booking);
        long delivery = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'DELIVERY'", Long.class, booking);
        long bookingHorse = jdbc.queryForObject("SELECT id FROM booking_horse WHERE booking_id = ?", Long.class, booking);
        chip = jdbc.queryForObject("SELECT h.microchip FROM booking_horse bh JOIN horse h ON h.id = bh.horse_id WHERE bh.id = ?", String.class, bookingHorse);
        long hcm = locations.findByCode("HUB-HCM").orElseThrow().getId();
        Instant start = jdbc.queryForObject("SELECT requested_pickup_date FROM booking WHERE id = ?", java.sql.Date.class, booking)
                .toLocalDate().atTime(1, 0).toInstant(ZoneOffset.UTC);

        String body = "{\"tripType\":\"DIRECT\",\"vehicleId\":%d,\"stops\":[%s,%s,%s,%s]}".formatted(testData.vehicle(2, 0).getId(),
                stop("locationId", hcm, "HUB", start, 0), stop("bookingAddressId", pickup, "PICKUP", start, 60),
                stop("bookingAddressId", delivery, "DELIVERY", start, 240), stop("locationId", hcm, "HUB", start, 480));
        trip = id(mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON).content(body)));
        stops = ((List<Number>) JsonPath.read(content(get("/vehicle-trips/" + trip), coordinator), "$.data.stops[*].id")).stream().map(Number::longValue).toList();
        for (Staff member : List.of(driverStaff, escortStaff)) {
            mvc.perform(post("/vehicle-trips/{id}/crew-assignments", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"staffId\":%d,\"crewRole\":\"%s\"}".formatted(member.getUserId(), member == driverStaff ? "DRIVER" : "ESCORT")))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/vehicle-trips/{id}/horse-legs", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingHorseId\":%d,\"boardTripStopId\":%d,\"alightTripStopId\":%d}".formatted(bookingHorse, stops.get(1), stops.get(2))))
                .andExpect(status().isCreated());
        leg = jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND status = 'PLANNED'", Long.class, bookingHorse);
    }

    @Test
    void driverReportsMaintenanceAtCompletionAndDispatchCannotUseTheVehicleAgain() throws Exception {
        Staff admin = testData.staff(testData.unique("exec.admin"), "ADMIN");
        Staff dispatcher = testData.staff(testData.unique("exec.notice"), "COORDINATOR");
        long vehicle = readyToComplete();
        Instant nextStart = Instant.now().plusSeconds(86400 * 30);
        long nextTrip = id(planTransfer(vehicle, nextStart));
        String nextCode = jdbc.queryForObject("SELECT code FROM vehicle_trip WHERE id = ?", String.class, nextTrip);
        Staff nextDriver = testData.staff(testData.unique("exec.next.driver"), "DRIVER");
        String nextDriverToken = token(nextDriver, "DRIVER");
        mvc.perform(post("/vehicle-trips/{id}/crew-assignments", nextTrip).header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":%d,\"crewRole\":\"DRIVER\"}".formatted(nextDriver.getUserId())))
                .andExpect(status().isCreated());
        acknowledge(nextDriverToken);
        TripReturnPhotoFixtures.attach(jdbc, trip);

        mvc.perform(post("/vehicle-trips/{id}/complete", trip).header(HttpHeaders.AUTHORIZATION, driver)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"endVehicleCondition":"NEEDS_MAINTENANCE","endVehicleNote":"  Phanh có tiếng kêu  ",
                         "id":9999,"status":"CANCELLED","vehicleId":9999}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trip.id").value(trip))
                .andExpect(jsonPath("$.data.trip.vehicleId").value(vehicle))
                .andExpect(jsonPath("$.data.trip.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.trip.endVehicleCondition").value("NEEDS_MAINTENANCE"))
                .andExpect(jsonPath("$.data.trip.endVehicleNote").value("Phanh có tiếng kêu"));

        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM vehicle WHERE id = ?", String.class, vehicle)).isEqualTo("MAINTENANCE");
        assertThat(jdbc.queryForObject("SELECT end_vehicle_condition FROM vehicle_trip WHERE id = ?", String.class, trip))
                .isEqualTo("NEEDS_MAINTENANCE");
        assertThat(jdbc.queryForList("""
                SELECT new_data ->> 'reason' FROM audit_log
                WHERE target_table = 'vehicle' AND target_id = ? AND new_data ->> 'status' = 'MAINTENANCE'
                """, String.class, vehicle)).containsExactly("Phanh có tiếng kêu");
        assertThat(jdbc.queryForList("""
                SELECT r.user_id FROM notification n JOIN notification_recipient r ON r.notification_id = n.id
                WHERE n.message LIKE ? AND r.user_id IN (?, ?)
                """, Long.class, "%" + nextCode + "%", admin.getUserId(), dispatcher.getUserId()))
                .containsExactlyInAnyOrder(admin.getUserId(), dispatcher.getUserId());
        planTransfer(vehicle, nextStart.plusSeconds(86400)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.vehicleId").value("Xe đang MAINTENANCE, không gán vào lượt mới được."));
        act(nextDriverToken, "/vehicle-trips/" + nextTrip + "/start").andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.vehicleId").isNotEmpty());
    }

    @Test
    void completionWithoutBodyDefaultsToNormal() throws Exception {
        long vehicle = readyToComplete();
        TripReturnPhotoFixtures.attach(jdbc, trip);

        act(driver, "/vehicle-trips/" + trip + "/complete").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trip.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.trip.endVehicleCondition").value("NORMAL"));

        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM vehicle WHERE id = ?", String.class, vehicle)).isEqualTo("ACTIVE");
    }

    @Test
    void driverCannotReportMaintenanceWithoutANote() throws Exception {
        long vehicle = readyToComplete();
        TripReturnPhotoFixtures.attach(jdbc, trip);

        mvc.perform(post("/vehicle-trips/{id}/complete", trip).header(HttpHeaders.AUTHORIZATION, driver)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"endVehicleCondition":"NEEDS_MAINTENANCE","endVehicleNote":"   "}
                        """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.endVehicleNote").isNotEmpty());

        assertThat(jdbc.queryForObject("SELECT status FROM vehicle_trip WHERE id = ?", String.class, trip)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT status FROM vehicle WHERE id = ?", String.class, vehicle)).isEqualTo("ACTIVE");
    }

    private long readyToComplete() {
        entityManager.flush();
        jdbc.update("UPDATE vehicle_trip SET status = 'IN_PROGRESS', actual_start_at = now() WHERE id = ?", trip);
        jdbc.update("UPDATE trip_stop SET actual_arrival_at = now(), actual_departure_at = now() WHERE vehicle_trip_id = ?", trip);
        jdbc.update("UPDATE horse_leg SET status = 'COMPLETED', actual_start_at = now(), actual_end_at = now() WHERE id = ?", leg);
        entityManager.clear();
        return jdbc.queryForObject("SELECT vehicle_id FROM vehicle_trip WHERE id = ?", Long.class, trip);
    }

    private ResultActions planTransfer(long vehicle, Instant start) throws Exception {
        long hub = locations.findByCode("HUB-HCM").orElseThrow().getId();
        return mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"tripType":"TRANSFER","vehicleId":%d,"stops":[%s,%s]}
                        """.formatted(vehicle, stop("locationId", hub, "HUB", start, 0),
                        stop("locationId", hub, "HUB", start, 60))));
    }

    @Test
    void departureStartsAtTheFirstStopAndWaitsForAllNonCancelledLinehaulTrips() throws Exception {
        long departure = jdbc.queryForObject("SELECT departure_id FROM booking WHERE code = 'DEV-DOMESTIC-001'", Long.class);
        jdbc.update("""
                UPDATE departure SET run_confirmed_at = now(), run_confirmed_by = (SELECT min(id) FROM app_user)
                WHERE id = ?
                """, departure);
        long first = emptyLinehaul(departure);
        long second = emptyLinehaul(departure);
        Staff member = testData.staff(testData.unique("exec.linehaul"), "DRIVER");
        String assigned = token(member, "DRIVER");
        mvc.perform(post("/vehicle-trips/{id}/crew-assignments", first).header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"staffId\":%d,\"crewRole\":\"DRIVER\"}".formatted(member.getUserId())))
                .andExpect(status().isCreated());
        List<Long> linehaulStops = jdbc.queryForList(
                "SELECT id FROM trip_stop WHERE vehicle_trip_id = ? ORDER BY sequence_number", Long.class, first);

        acknowledge(assigned);
        act(assigned, "/vehicle-trips/" + first + "/start").andExpect(status().isOk());
        assertThat(departureStatus(departure)).isIn("OPEN", "CLOSED");
        act(assigned, "/trip-stops/" + linehaulStops.getFirst() + "/arrive").andExpect(status().isOk());
        act(assigned, "/trip-stops/" + linehaulStops.getFirst() + "/depart").andExpect(status().isOk());
        entityManager.flush();
        assertThat(departureStatus(departure)).isEqualTo("IN_PROGRESS");

        jdbc.update("UPDATE trip_stop SET actual_arrival_at = now() WHERE id = ?", linehaulStops.getLast());
        entityManager.clear();
        TripReturnPhotoFixtures.attach(jdbc, first);
        act(assigned, "/vehicle-trips/" + first + "/complete").andExpect(status().isOk());
        assertThat(departureStatus(departure)).isEqualTo("IN_PROGRESS");
        mvc.perform(post("/vehicle-trips/{id}/cancel", second).header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Không cần xe thứ hai\"}")).andExpect(status().isOk());
        entityManager.flush();
        assertThat(departureStatus(departure)).isEqualTo("COMPLETED");
    }

    private long emptyLinehaul(long departureId) throws Exception {
        return id(mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripType\":\"LINEHAUL\",\"vehicleId\":%d,\"departureId\":%d}"
                        .formatted(testData.vehicle(2, 0).getId(), departureId))).andExpect(status().isCreated()));
    }

    @Test
    void linehaulCannotLeaveBorderUntilOnboardBookingHasItsDocuments() throws Exception {
        long international = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-EXPORT-001'", Long.class);
        long departure = jdbc.queryForObject("SELECT departure_id FROM booking WHERE id = ?", Long.class, international);
        jdbc.update("UPDATE departure SET run_confirmed_at = now(), run_confirmed_by = ? WHERE id = ?", coordinatorId, departure);
        long linehaul = emptyLinehaul(departure);
        Staff member = testData.staff(testData.unique("exec.border"), "DRIVER");
        mvc.perform(post("/vehicle-trips/{id}/crew-assignments", linehaul).header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":%d,\"crewRole\":\"DRIVER\"}".formatted(member.getUserId())))
                .andExpect(status().isCreated());
        entityManager.flush();
        jdbc.update("UPDATE vehicle_trip SET status = 'IN_PROGRESS', actual_start_at = now() WHERE id = ?", linehaul);
        jdbc.update("UPDATE trip_stop SET actual_arrival_at = now() WHERE vehicle_trip_id = ? AND stop_type = 'BORDER'", linehaul);
        jdbc.update("""
                INSERT INTO horse_leg (booking_horse_id, sequence_number, leg_type, from_location_id, to_location_id, vehicle_trip_id,
                        board_trip_stop_id, alight_trip_stop_id, planned_start_at, planned_end_at, actual_start_at, status)
                SELECT bh.id, 1, 'LINEHAUL', v.start_location_id, v.end_location_id, v.id,
                    (SELECT id FROM trip_stop WHERE vehicle_trip_id = v.id ORDER BY sequence_number LIMIT 1),
                    (SELECT id FROM trip_stop WHERE vehicle_trip_id = v.id ORDER BY sequence_number DESC LIMIT 1),
                    v.planned_start_at, v.planned_end_at, now(), 'IN_PROGRESS'
                FROM booking_horse bh CROSS JOIN vehicle_trip v WHERE bh.booking_id = ? AND v.id = ?
                """, international, linehaul);
        jdbc.update("UPDATE document SET status = 'REJECTED' WHERE booking_id = ? OR booking_horse_id IN (SELECT id FROM booking_horse WHERE booking_id = ?)",
                international, international);
        entityManager.clear();
        long border = jdbc.queryForObject("SELECT id FROM trip_stop WHERE vehicle_trip_id = ? AND stop_type = 'BORDER'", Long.class, linehaul);
        act(token(member, "DRIVER"), "/trip-stops/" + border + "/depart").andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.tripStopId", org.hamcrest.Matchers.containsString("DEV-EXPORT-001 thiếu")));
        assertThat(jdbc.queryForObject("SELECT actual_departure_at FROM trip_stop WHERE id = ?", java.sql.Timestamp.class, border)).isNull();
    }

    @Test
    void pickupCannotLeaveRestStopWithoutEveryHorsesFeedAndWaterLog() throws Exception {
        long rest = locations.findByCode("REST-SONLA").orElseThrow().getId();
        entityManager.flush();
        jdbc.update("UPDATE vehicle_trip SET trip_type = 'PICKUP', status = 'IN_PROGRESS', actual_start_at = now() WHERE id = ?", trip);
        jdbc.update("""
                UPDATE trip_stop SET stop_type = 'REST', location_id = ?, booking_address_id = NULL,
                    actual_arrival_at = now() - interval '40 minutes' WHERE id = ?
                """, rest, stops.get(2));
        jdbc.update("""
                UPDATE horse_leg SET leg_type = 'PICKUP', status = 'IN_PROGRESS', actual_start_at = now() - interval '4 hours',
                    alight_trip_stop_id = ? WHERE id = ?
                """, stops.getLast(), leg);
        entityManager.clear();
        act(driver, "/trip-stops/" + stops.get(2) + "/depart").andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.healthLogs", org.hamcrest.Matchers.containsString("Chưa ghi ăn uống")));
    }

    private String departureStatus(long departureId) {
        return jdbc.queryForObject("SELECT status FROM departure WHERE id = ?", String.class, departureId);
    }

    @Test
    void tripWithHorsesNeedsAnAcknowledgedEscort() throws Exception {
        makeLocal();
        acknowledge(driver);

        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Lượt chở ngựa phải có hộ tống đã nhận việc."));

        acknowledge(escort);
        mvc.perform(post("/vehicle-trips/{id}/start", trip).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.earlyReason").exists());
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trip.status").value("IN_PROGRESS"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_table = 'vehicle_trip' AND target_id = ? AND action = 'EARLY_TRIP_START'",
                Long.class, trip)).isEqualTo(1L);
    }

    @Test
    void highIncidentBlocksArrivalUntilTheManagerApprovesThePlan() throws Exception {
        startTripForIncidents();
        long firstStop = stops.getFirst();
        long incident = id(reportIncident(null, "ACCIDENT", null)
                .andExpect(jsonPath("$.data.severity").value("HIGH"))
                .andExpect(jsonPath("$.data.blocksTrip").value(true)));
        act(driver, "/trip-stops/" + firstStop + "/arrive").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(INCIDENT_BLOCK_MESSAGE));

        String proposal = mvc.perform(post("/incidents/{id}/plans", incident).header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"resolutionType":"CONTINUE","instructions":"Đã kiểm tra xe, chờ duyệt để chạy tiếp"}
                        """)).andExpect(status().isCreated()).andExpect(jsonPath("$.data.blocksTrip").value(true))
                .andReturn().getResponse().getContentAsString();
        long plan = ((Number) JsonPath.read(proposal, "$.data.plans[0].id")).longValue();
        act(driver, "/trip-stops/" + firstStop + "/arrive").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(INCIDENT_BLOCK_MESSAGE));
        act(managerToken(), "/incident-plans/" + plan + "/approve").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLAN_ACTIVE")).andExpect(jsonPath("$.data.blocksTrip").value(false));
        act(driver, "/trip-stops/" + firstStop + "/arrive").andExpect(status().isOk());
        act(driver, "/trip-stops/" + firstStop + "/depart").andExpect(status().isOk());
    }

    @Test
    void highIncidentBlocksDepartureUntilDispatchUnlocksItWithAReason() throws Exception {
        startTripForIncidents();
        long firstStop = stops.getFirst();
        act(driver, "/trip-stops/" + firstStop + "/arrive").andExpect(status().isOk());
        long incident = id(reportIncident(leg, "HEALTH", null).andExpect(jsonPath("$.data.severity").value("HIGH")));
        entityManager.flush();
        // Dữ liệu cũ có thể gắn lượt qua chặng ngựa, không có vehicle_trip_id trực tiếp.
        jdbc.update("UPDATE incident SET vehicle_trip_id = NULL WHERE id = ?", incident);
        entityManager.clear();
        mvc.perform(get("/vehicle-trips/{id}/incidents", trip).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].blocksTrip").value(true));
        act(driver, "/trip-stops/" + firstStop + "/depart").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(INCIDENT_BLOCK_MESSAGE));

        unlockTrip(incident, managerToken(), "{\"reason\":\"Đã kiểm tra\"}").andExpect(status().isForbidden());
        unlockTrip(incident, coordinator, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.reason").isNotEmpty());
        unlockTrip(incident, coordinator, "{\"reason\":\"  \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.reason").isNotEmpty());
        act(driver, "/trip-stops/" + firstStop + "/depart").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(INCIDENT_BLOCK_MESSAGE));
        unlockTrip(incident, coordinator, "{\"reason\":\"  Đã kiểm tra an toàn  \"}").andExpect(status().isOk());
        unlockTrip(incident, coordinator, "{\"reason\":\"Gửi lại yêu cầu\"}").andExpect(status().isConflict());
        entityManager.flush();
        assertThat(jdbc.queryForObject("""
                SELECT trip_unlocked_at IS NOT NULL AND trip_unlocked_by = ?
                    AND trip_unlock_reason = 'Đã kiểm tra an toàn' AND status = 'REPORTED'
                FROM incident WHERE id = ?
                """, Boolean.class, coordinatorId, incident)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log WHERE target_table = 'incident' AND target_id = ?
                    AND action = 'UNLOCK_TRIP_FOR_INCIDENT' AND actor_user_id = ?
                    AND new_data ->> 'reason' = 'Đã kiểm tra an toàn'
                """, Integer.class, incident, coordinatorId)).isEqualTo(1);
        mvc.perform(get("/vehicle-trips/{id}/incidents", trip).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].blocksTrip").value(false));
        act(driver, "/trip-stops/" + firstStop + "/depart").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isOk());
    }

    @Test
    void crewCanReportLowSeverityForAHealthIncidentAndKeepMoving() throws Exception {
        startTripForIncidents();
        reportIncident(leg, "HEALTH", "LOW").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.severity").value("LOW")).andExpect(jsonPath("$.data.blocksTrip").value(false));
        long firstStop = stops.getFirst();
        act(driver, "/trip-stops/" + firstStop + "/arrive").andExpect(status().isOk());
        act(driver, "/trip-stops/" + firstStop + "/depart").andExpect(status().isOk());
    }

    private void startTripForIncidents() throws Exception {
        makeLocal();
        acknowledge(driver);
        acknowledge(escort);
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk());
    }

    private String managerToken() {
        return token(testData.staff(testData.unique("exec.incident.manager"), "MANAGER"), "MANAGER");
    }

    private ResultActions reportIncident(Long horseLegId, String type, String severity) throws Exception {
        String severityJson = severity == null ? "" : ", \"severity\":\"%s\"".formatted(severity);
        return mvc.perform(post("/incidents").header(HttpHeaders.AUTHORIZATION, escort).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vehicleTripId":%d,"horseLegId":%s,"incidentType":"%s","causeType":"COMPANY_FAULT",
                         "title":"Sự cố trên đường","description":"Đã dừng để kiểm tra"%s}
                        """.formatted(trip, horseLegId, type, severityJson)));
    }

    private ResultActions unlockTrip(long incident, String actor, String body) throws Exception {
        return mvc.perform(post("/incidents/{id}/unlock-trip", incident).header(HttpHeaders.AUTHORIZATION, actor)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void crewRunsTheWholeDirectTrip() throws Exception {
        // Đơn nội tỉnh không cần giấy bắt buộc (DocumentCompletenessService của luồng A); ca thiếu giấy ở test riêng
        makeLocal();
        // Tài xế chưa xác nhận nhận việc thì chưa xuất phát
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tài xế chưa xác nhận nhận việc."));
        acknowledge(driver);
        acknowledge(escort);
        act(escort, "/vehicle-trips/" + trip + "/start").andExpect(status().isForbidden());
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk()).andExpect(jsonPath("$.data.trip.status").value("IN_PROGRESS"));

        // Đi theo thứ tự điểm dừng
        act(driver, "/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa rời điểm dừng trước."));
        act(driver, "/trip-stops/" + stops.get(0) + "/arrive").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/depart").andExpect(status().isOk());
        act(escort, "/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isForbidden());
        act(driver, "/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isConflict());
        mvc.perform(get("/crew/vehicle-trips/{id}/departure-readiness", trip).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stopId").value(stops.get(1).intValue()))
                .andExpect(jsonPath("$.data.waitingForEscort").value(true));

        // Khách mới trả cọc: số dư lập khi nhận ngựa xong (seed có sẵn số dư đã trả nên bỏ đi)
        entityManager.flush();
        jdbc.update("DELETE FROM payment WHERE booking_id = ? AND payment_type = 'BALANCE'", booking);
        entityManager.clear();

        // Giao nhận ở điểm đón: loại sai với điểm dừng bị từ chối
        handover(driver, stops.get(1), "PICKUP").andExpect(status().isForbidden());
        handover(escort, stops.get(1), "DELIVERY").andExpect(status().isBadRequest());
        long pickup = handoverId(handover(escort, stops.get(1), "PICKUP").andExpect(status().isCreated()));
        handover(escort, stops.get(1), "PICKUP").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.handovers.length()").value(1))
                .andExpect(jsonPath("$.data.handovers[0].id").value((int) pickup));
        scan(driver, pickup, chip).andExpect(status().isForbidden());
        act(escort, "/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isForbidden());
        act(driver, "/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Còn lần giao nhận chưa xong ở điểm dừng này."));
        scan(escort, pickup, "985000000999999").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handovers[0].horses[0].microchipMatched").value(false));
        act(escort, "/handovers/" + pickup + "/complete").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa có con nào quét đúng chip."));
        scan(escort, pickup, chip).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handovers[0].horses[0].microchipMatched").value(true));
        // MAN_HINH 4.3: chưa có biên bản đã ký thì chưa hoàn tất nhận ngựa
        jdbc.update("DELETE FROM document WHERE handover_id = ?", pickup);
        act(escort, "/handovers/" + pickup + "/complete").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa có biên bản giao nhận. Chụp biên bản đã ký trước khi hoàn tất."));
        signedRecord(pickup);
        mvc.perform(post("/handovers/{id}/complete", pickup).header(HttpHeaders.AUTHORIZATION, escort))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cần ít nhất một ảnh cho từng con ngựa."));
        act(escort, "/handovers/" + pickup + "/complete").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handovers[0].recordAttached").value(true))
                .andExpect(jsonPath("$.data.handovers[0].status").value("DONE"))
                .andExpect(jsonPath("$.data.horses[0].leg.status").value("IN_PROGRESS"));
        handover(escort, stops.get(1), "PICKUP").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.handovers.length()").value(1))
                .andExpect(jsonPath("$.data.handovers[0].status").value("DONE"));

        // Phiếu trùng từ trước khi sửa có thể vẫn đang mở và có chứng từ riêng.
        long duplicate = jdbc.queryForObject("""
                INSERT INTO handover (handover_type, trip_stop_id, handled_by, status)
                SELECT handover_type, trip_stop_id, handled_by, 'PENDING' FROM handover WHERE id = ?
                RETURNING id
                """, Long.class, pickup);
        scan(escort, duplicate, chip).andExpect(status().isOk());
        signedRecord(duplicate);
        act(driver, "/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Còn lần giao nhận chưa xong ở điểm dừng này."));
        act(escort, "/handovers/" + duplicate + "/complete").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handovers[0].status").value("DONE"))
                .andExpect(jsonPath("$.data.handovers[1].status").value("FAILED"))
                .andExpect(jsonPath("$.data.handovers[1].recordAttached").value(true))
                .andExpect(jsonPath("$.data.handovers[1].horses[0].photoAttached").value(true))
                .andExpect(jsonPath("$.data.horses[0].leg.status").value("IN_PROGRESS"));
        mvc.perform(post("/handovers/{id}/complete", pickup).header(HttpHeaders.AUTHORIZATION, escort))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.handovers[0].status").value("DONE"));

        // Nhận xong thì lập khoản số dư = tổng báo giá trừ tiền cọc, hạn tính từ lúc nhận
        var balance = jdbc.queryForMap("""
                SELECT p.amount, p.status, p.due_at, h.handed_at, q.total_amount - q.deposit_amount AS expected
                FROM payment p JOIN quotation q ON q.id = p.quotation_id, handover h
                WHERE p.booking_id = ? AND p.payment_type = 'BALANCE' AND h.id = ?""", booking, pickup);
        assertThat(balance.get("status")).isEqualTo("PENDING");
        assertThat((java.math.BigDecimal) balance.get("amount")).isEqualByComparingTo((java.math.BigDecimal) balance.get("expected"));
        assertThat(((java.sql.Timestamp) balance.get("due_at")).toInstant()).isAfter(((java.sql.Timestamp) balance.get("handed_at")).toInstant());

        // Quy tắc #32: chưa trả đủ thì xe không rời nơi nhận
        act(driver, "/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Đơn DEV-DIRECT-001 chưa trả đủ")));
        entityManager.flush();
        jdbc.update("UPDATE payment SET status = 'PAID', paid_at = now() WHERE booking_id = ? AND payment_type = 'BALANCE'", booking);
        entityManager.clear();
        mvc.perform(get("/crew/vehicle-trips/{id}/departure-readiness", trip).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stopId").value(stops.get(1).intValue()))
                .andExpect(jsonPath("$.data.waitingForEscort").value(false));
        act(driver, "/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isOk());
        assertThat(bookingStatus()).isEqualTo("IN_OPERATION");

        // Chưa giao xong thì chưa kết thúc lượt
        act(driver, "/trip-stops/" + stops.get(2) + "/arrive").andExpect(status().isOk());
        long delivery = handoverId(handover(escort, stops.get(2), "DELIVERY").andExpect(status().isCreated()));
        scan(escort, delivery, chip).andExpect(status().isOk());
        act(escort, "/handovers/" + delivery + "/complete").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.horses[0].leg.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.trip.returning").value(true));
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, leg)).isEqualTo("COMPLETED");
        assertThat(bookingStatus()).isEqualTo("DELIVERED");
        // B6: khách được báo khi ngựa được nhận và khi được giao
        assertThat(customerNotices(booking)).contains("Đã nhận ngựa", "Đã giao ngựa");

        act(driver, "/trip-stops/" + stops.get(2) + "/depart").andExpect(status().isOk());
        act(driver, "/vehicle-trips/" + trip + "/complete").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa tới điểm dừng cuối."));
        act(driver, "/trip-stops/" + stops.get(3) + "/arrive").andExpect(status().isOk());
        TripReturnPhotoFixtures.attach(jdbc, trip);
        act(driver, "/vehicle-trips/" + trip + "/complete").andExpect(status().isOk()).andExpect(jsonPath("$.data.trip.status").value("COMPLETED"));
    }

    @Test
    void recordsChipEntryMethodInHandoverDetailsAndStaffJourney() throws Exception {
        makeLocal();
        jdbc.update("DELETE FROM payment WHERE booking_id = ? AND payment_type = 'BALANCE'", booking);
        acknowledge(driver);
        acknowledge(escort);
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/arrive").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/depart").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isOk());

        String horse = "$.data.handovers[0].horses[0]";
        long pickup = handoverId(handover(escort, stops.get(1), "PICKUP").andExpect(status().isCreated())
                .andExpect(jsonPath(horse + ".entryMethod").doesNotExist()));
        scan(escort, pickup, chip).andExpect(status().isOk())
                .andExpect(jsonPath(horse + ".entryMethod").value("SCAN"))
                .andExpect(jsonPath(horse + ".manualReason").doesNotExist());
        chipEntry(pickup, "MANUAL", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.manualReason").value("Vui lòng ghi lý do gõ tay mã chip."));
        chipEntry(pickup, "MANUAL", " ".repeat(501)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.manualReason").exists());

        String reason = "Máy đọc bị hỏng";
        chipEntry(pickup, "MANUAL", "  " + reason + "  ").andExpect(status().isOk())
                .andExpect(jsonPath(horse + ".entryMethod").value("MANUAL"))
                .andExpect(jsonPath(horse + ".manualReason").value(reason))
                .andExpect(jsonPath(horse + ".microchipMatched").value(true));
        chipEntry(pickup, "SCAN", reason).andExpect(status().isOk())
                .andExpect(jsonPath(horse + ".entryMethod").value("SCAN"))
                .andExpect(jsonPath(horse + ".manualReason").doesNotExist());
        chipEntry(pickup, "MANUAL", reason).andExpect(status().isOk());
        act(escort, "/handovers/" + pickup + "/complete").andExpect(status().isOk());

        String event = "$.data.horses[0].handovers[0]";
        mvc.perform(get("/bookings/{id}/journey", booking).header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk())
                .andExpect(jsonPath(event + ".entryMethod").value("MANUAL"))
                .andExpect(jsonPath(event + ".manualReason").value(reason));
        String customer = "Bearer " + jwtUtils.generateToken("customer1.dev", List.of("CUSTOMER"));
        mvc.perform(get("/bookings/{id}/journey", booking).header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath(event + ".microchipMatched").value(true))
                .andExpect(jsonPath(event + ".entryMethod").doesNotHaveJsonPath())
                .andExpect(jsonPath(event + ".manualReason").doesNotHaveJsonPath());

        entityManager.flush();
        jdbc.update("UPDATE payment SET due_at = now() - interval '1 minute' WHERE booking_id = ? AND payment_type = 'BALANCE'", booking);
        entityManager.clear();
        handover(escort, stops.get(1), "RETURN").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.handovers[1].horses[0].entryMethod").value("MANUAL"))
                .andExpect(jsonPath("$.data.handovers[1].horses[0].manualReason").value(reason));
    }

    @Test
    void overdueBalanceReturnsTheHorseAndLeavesTheBookingAvailableForRescheduling() throws Exception {
        makeLocal();
        entityManager.flush();
        jdbc.update("DELETE FROM payment WHERE booking_id = ? AND payment_type = 'BALANCE'", booking);
        entityManager.clear();
        acknowledge(driver);
        acknowledge(escort);
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/arrive").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/depart").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isOk());
        // Chưa nhận con nào thì không có gì để trả
        handover(escort, stops.get(1), "RETURN").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không có con ngựa nào cần giao nhận loại này ở điểm dừng này."));
        long pickup = handoverId(handover(escort, stops.get(1), "PICKUP").andExpect(status().isCreated()));
        scan(escort, pickup, chip).andExpect(status().isOk());
        act(escort, "/handovers/" + pickup + "/complete").andExpect(status().isOk());

        // Còn trong hạn trả số dư: chưa được trả ngựa do quá hạn
        handover(escort, stops.get(1), "RETURN").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Đơn chưa đủ điều kiện trả ngựa vì quá thời gian chờ thanh toán."));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM handover WHERE trip_stop_id = ? AND handover_type = 'RETURN'", Long.class, stops.get(1))).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, leg)).isEqualTo("IN_PROGRESS");

        // Trả ngựa tại chỗ, vẫn giữ khoản số dư để khách thanh toán khi chọn lại lịch
        entityManager.flush();
        jdbc.update("UPDATE payment SET due_at = now() - interval '1 minute' WHERE booking_id = ? AND payment_type = 'BALANCE'", booking);
        entityManager.clear();
        handover(escort, stops.get(1), "RETURN").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.handovers[1].handoverType").value("RETURN"))
                .andExpect(jsonPath("$.data.handovers[1].status").value("DONE"))
                .andExpect(jsonPath("$.data.handovers[1].horses[0].scannedMicrochip").value(chip));
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, leg)).isEqualTo("CANCELLED");
        assertThat(bookingStatus()).isEqualTo("SCHEDULE_CHANGE_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE booking_id = ? AND payment_type = 'BALANCE'", String.class, booking))
                .isEqualTo("PENDING");
        handover(escort, stops.get(1), "RETURN").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM handover WHERE trip_stop_id = ? AND handover_type = 'RETURN'",
                Long.class, stops.get(1))).isEqualTo(1L);
        act(driver, "/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isOk());
    }

    @Test
    void onlyCrewOfTheTripOperatesIt() throws Exception {
        acknowledge(driver);
        String stranger = token(testData.staff(testData.unique("exec.stranger"), "DRIVER"), "DRIVER");
        mvc.perform(get("/crew/vehicle-trips/{id}", trip).header(HttpHeaders.AUTHORIZATION, stranger)).andExpect(status().isForbidden());
        act(stranger, "/vehicle-trips/" + trip + "/start").andExpect(status().isForbidden());
        act(coordinator, "/vehicle-trips/" + trip + "/start").andExpect(status().isForbidden());
        mvc.perform(get("/crew/vehicle-trips/{id}", trip).header(HttpHeaders.AUTHORIZATION, escort))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.trip.stops.length()").value(4));
    }

    @Test
    void onlyHorsesWithMatchingChipAreReceived() throws Exception {
        long domestic = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DOMESTIC-001'", Long.class);
        long pickup = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'PICKUP'", Long.class, domestic);
        List<Long> lines = jdbc.queryForList("SELECT id FROM booking_horse WHERE booking_id = ? ORDER BY id", Long.class, domestic);
        long hcm = locations.findByCode("HUB-HCM").orElseThrow().getId();
        Instant start = jdbc.queryForObject("SELECT planned_departure_at FROM departure d JOIN booking b ON b.departure_id = d.id WHERE b.id = ?",
                java.sql.Timestamp.class, domestic).toInstant().minusSeconds(10 * 3600);
        Staff driverStaff = testData.staff(testData.unique("exec.driver2"), "DRIVER");
        Staff escortStaff = testData.staff(testData.unique("exec.escort2"), "ESCORT");
        String driver2 = token(driverStaff, "DRIVER");
        String escort2 = token(escortStaff, "ESCORT");
        long pickupTrip = id(mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripType\":\"PICKUP\",\"vehicleId\":%d,\"stops\":[%s,%s,%s]}".formatted(testData.vehicle(2, 0).getId(),
                        stop("locationId", hcm, "HUB", start, 0), stop("bookingAddressId", pickup, "PICKUP", start, 60), stop("locationId", hcm, "HUB", start, 240)))));
        List<Long> pickupStops = ((List<Number>) JsonPath.read(content(get("/vehicle-trips/" + pickupTrip), coordinator), "$.data.stops[*].id"))
                .stream().map(Number::longValue).toList();
        for (Staff member : List.of(driverStaff, escortStaff)) {
            mvc.perform(post("/vehicle-trips/{id}/crew-assignments", pickupTrip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"staffId\":%d,\"crewRole\":\"%s\"}".formatted(member.getUserId(), member == driverStaff ? "DRIVER" : "ESCORT")))
                    .andExpect(status().isCreated());
        }
        for (long line : lines) {
            mvc.perform(post("/vehicle-trips/{id}/horse-legs", pickupTrip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bookingHorseId\":%d,\"boardTripStopId\":%d,\"alightTripStopId\":%d}".formatted(line, pickupStops.get(1), pickupStops.get(2))))
                    .andExpect(status().isCreated());
        }
        entityManager.flush();
        jdbc.update("UPDATE booking SET booking_type = 'LOCAL' WHERE id = ?", domestic);
        entityManager.clear();
        acknowledge(driver2);
        acknowledge(escort2);
        act(driver2, "/vehicle-trips/" + pickupTrip + "/start").andExpect(status().isOk());
        act(driver2, "/trip-stops/" + pickupStops.get(0) + "/arrive").andExpect(status().isOk());
        act(driver2, "/trip-stops/" + pickupStops.get(0) + "/depart").andExpect(status().isOk());
        act(driver2, "/trip-stops/" + pickupStops.get(1) + "/arrive").andExpect(status().isOk());
        long handover = handoverId(handover(escort2, pickupStops.get(1), "PICKUP").andExpect(status().isCreated()));

        long first = jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND vehicle_trip_id = ?", Long.class, lines.get(0), pickupTrip);
        long second = jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND vehicle_trip_id = ?", Long.class, lines.get(1), pickupTrip);
        String firstChip = jdbc.queryForObject("SELECT h.microchip FROM booking_horse bh JOIN horse h ON h.id = bh.horse_id WHERE bh.id = ?", String.class, lines.get(0));
        mvc.perform(post("/handovers/{id}/horses", handover).header(HttpHeaders.AUTHORIZATION, escort2).contentType(MediaType.APPLICATION_JSON)
                .content("{\"horseLegId\":%d,\"conditionNote\":\"Khỏe\",\"microchip\":\"%s\"}".formatted(first, firstChip))).andExpect(status().isOk());
        mvc.perform(post("/handovers/{id}/horses", handover).header(HttpHeaders.AUTHORIZATION, escort2).contentType(MediaType.APPLICATION_JSON)
                .content("{\"horseLegId\":%d,\"microchip\":\"985000000999999\"}".formatted(second))).andExpect(status().isOk());

        act(escort2, "/handovers/" + handover + "/complete").andExpect(status().isConflict());
        act(driver2, "/trip-stops/" + pickupStops.get(1) + "/depart").andExpect(status().isConflict());
        refuse(escort2, handover, second, " ").andExpect(status().isBadRequest());
        refuse(escort2, handover, second, "Chip không khớp giấy tờ").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handovers[0].horses.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT status FROM booking_horse WHERE id = ?", String.class, lines.get(1))).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, second)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForList("SELECT status FROM quotation WHERE booking_id = ? ORDER BY version", String.class, domestic))
                .containsExactly("ACCEPTED");
        act(escort2, "/handovers/" + handover + "/complete").andExpect(status().isOk());
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/booking-horses/{id}/pickup-choices", lines.get(1))
                    .header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"choice\":\"DROP_HORSE\",\"phoneNote\":\"Khách xác nhận bỏ con qua điện thoại\"}"))
                    .andExpect(status().isOk());
        }
        // Quản lý chốt bên chịu xong mới bỏ con và tính lại tiền
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM booking_horse WHERE id = ?", String.class, lines.get(1))).isEqualTo("ACTIVE");
        String manager = token(testData.staff(testData.unique("exec.manager"), "MANAGER"), "MANAGER");
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/booking-horses/{id}/confirm-pickup-liability", lines.get(1))
                    .header(HttpHeaders.AUTHORIZATION, manager).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"liability\":\"CUSTOMER\"}")).andExpect(status().isOk());
        }
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM booking_horse WHERE id = ?", String.class, lines.get(1))).isEqualTo("EXCLUDED");
        assertThat(jdbc.queryForList("SELECT status FROM quotation WHERE booking_id = ? ORDER BY version", String.class, domestic))
                .containsExactly("SUPERSEDED", "ACCEPTED");
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, first)).isEqualTo("IN_PROGRESS");
        // Bảng phụ trách ghi nhận hộ tống đã ký nhận ngựa
        String row = "$.data[?(@.bookingHorseId == " + lines.get(0) + ")]";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/custody-board").header(HttpHeaders.AUTHORIZATION, coordinator)).andExpect(status().isOk())
                .andExpect(jsonPath(row + ".holder").value("COMPANY"))
                .andExpect(jsonPath(row + ".holderName").value(escortStaff.getFullName()))
                .andExpect(jsonPath(row + ".legType").value("PICKUP"))
                .andExpect(jsonPath(row + ".vehicleTripId").value((int) pickupTrip))
                .andExpect(jsonPath(row + ".careOverdue").value(false))
                .andExpect(jsonPath("$.data[?(@.bookingHorseId == " + lines.get(1) + ")]").isEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/custody-board").header(HttpHeaders.AUTHORIZATION, driver2)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, second)).isEqualTo("CANCELLED");
    }

    @Test
    void refusingTheOnlyHorseEndsThePickupAndTheVehicleLeaves() throws Exception {
        makeLocal();
        acknowledge(driver);
        acknowledge(escort);
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/arrive").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/depart").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isOk());
        long pickup = handoverId(handover(escort, stops.get(1), "PICKUP").andExpect(status().isCreated()));

        refuse(escort, pickup, leg, "Ngựa què chân trước").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handovers[0].status").value("FAILED"));
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, leg)).isEqualTo("CANCELLED");
        // Không con nào lên xe: không kiểm thanh toán, đơn không sang IN_OPERATION
        act(driver, "/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isOk());
        assertThat(bookingStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void tripDoesNotStartWhileDocumentsAreMissing() throws Exception {
        acknowledge(driver);
        acknowledge(escort);
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Đơn DEV-DIRECT-001 còn thiếu giấy tờ bắt buộc."));
        assertThat(jdbc.queryForObject("SELECT status FROM vehicle_trip WHERE id = ?", String.class, trip)).isEqualTo("PLANNED");
    }

    @Test
    void revokingAReusablePassportNotifiesTheCustomerAndBlocksTripStart() throws Exception {
        Staff specialist = assignDocumentSpecialist();
        String specialistToken = token(specialist, "SPECIALIST");
        long passport = approveRequiredDocuments("HORSE_PASSPORT");
        jdbc.update("UPDATE document SET document_requirement_id = NULL WHERE id = ?", passport);
        String customer = "Bearer " + jwtUtils.generateToken("customer1.dev", List.of("CUSTOMER"));
        String item = "$.data.items[?(@.requirement.documentTypeCode == 'HORSE_PASSPORT')].satisfied";
        mvc.perform(get("/bookings/{id}/documents/my-checklist", booking).header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isOk()).andExpect(jsonPath(item, contains(true)));

        revokeDocument(passport, customer, "Giấy sai nội dung").andExpect(status().isForbidden());
        Staff other = testData.staff(testData.unique("exec.doc.other"), "SPECIALIST");
        revokeDocument(passport, token(other, "SPECIALIST"), "Giấy sai nội dung").andExpect(status().isForbidden());
        revokeDocument(passport, specialistToken, "  ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.reason").value("Phải nhập lý do."));
        revokeDocument(passport, specialistToken, "  Giấy sai nội dung  ").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.reviewNote").value("Giấy sai nội dung"));
        revokeDocument(passport, specialistToken, "Gửi lại yêu cầu").andExpect(status().isConflict());
        entityManager.flush();
        assertThat(jdbc.queryForObject("""
                SELECT old_data ->> 'status' = 'APPROVED' AND new_data ->> 'status' = 'REJECTED'
                    AND new_data ->> 'reason' = 'Giấy sai nội dung'
                FROM audit_log WHERE action = 'REVOKE_DOCUMENT' AND target_table = 'document'
                    AND target_id = ? AND actor_user_id = ?
                """, Boolean.class, passport, specialist.getUserId())).isTrue();
        long customerId = jdbc.queryForObject("SELECT customer_id FROM booking WHERE id = ?", Long.class, booking);
        assertThat(documentNoticeRecipients("Giấy sai nội dung", "/orders/" + booking)).containsExactly(customerId);
        mvc.perform(get("/bookings/{id}/documents/my-checklist", booking).header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isOk()).andExpect(jsonPath(item, contains(false)));

        acknowledge(driver);
        acknowledge(escort);
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Đơn DEV-DIRECT-001 còn thiếu giấy tờ bắt buộc."));
    }

    @Test
    void revokingACompanyDocumentAfterTripStartNotifiesTheSpecialistAndBlocksPickup() throws Exception {
        Staff specialist = assignDocumentSpecialist();
        long certificate = approveRequiredDocuments("HEALTH_CERT");
        long pickup = startAndOpenPickup();

        revokeDocument(certificate, token(specialist, "SPECIALIST"), "Giấy kiểm dịch sai").andExpect(status().isOk());
        entityManager.flush();
        assertThat(documentNoticeRecipients("Giấy kiểm dịch sai", "/specialist/legal/" + booking))
                .containsExactly(specialist.getUserId());
        act(escort, "/handovers/" + pickup + "/complete").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Đơn DEV-DIRECT-001 còn thiếu giấy tờ bắt buộc."));
    }

    @Test
    void revocationIsBlockedAfterPickupEvenBeforeTheBookingIsInOperation() throws Exception {
        Staff specialist = assignDocumentSpecialist();
        long passport = approveRequiredDocuments("HORSE_PASSPORT");
        long pickup = startAndOpenPickup();
        act(escort, "/handovers/" + pickup + "/complete").andExpect(status().isOk());
        assertThat(bookingStatus()).isEqualTo("CONFIRMED");

        revokeDocument(passport, token(specialist, "SPECIALIST"), "Giấy sai nội dung").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Giấy đã theo xe, xử lý qua sự cố giấy tờ."));
        assertThat(jdbc.queryForObject("SELECT status FROM document WHERE id = ?", String.class, passport)).isEqualTo("APPROVED");
    }

    private Staff assignDocumentSpecialist() {
        Staff specialist = testData.staff(testData.unique("exec.doc.specialist"), "SPECIALIST");
        entityManager.flush();
        jdbc.update("UPDATE booking SET specialist_id = ? WHERE id = ?", specialist.getUserId(), booking);
        entityManager.clear();
        return specialist;
    }

    private long approveRequiredDocuments(String code) {
        return jdbc.queryForObject("""
                WITH approved AS (
                    INSERT INTO document (document_type_id, document_requirement_id, file_url, file_name, uploaded_by,
                            status, reviewed_by, reviewed_at, booking_id, booking_horse_id, horse_id)
                    SELECT dt.id, dr.id, 'test://approved/' || md5(random()::text), dt.code || '.pdf',
                           CASE WHEN dr.submitter_type = 'CUSTOMER' THEN b.customer_id ELSE b.specialist_id END,
                           'APPROVED', b.specialist_id, now(), CASE WHEN dt.owner_type = 'BOOKING' THEN b.id END,
                           CASE WHEN dt.owner_type = 'BOOKING_HORSE' THEN bh.id END,
                           CASE WHEN dt.owner_type = 'HORSE' THEN bh.horse_id END
                    FROM booking b JOIN booking_horse bh ON bh.booking_id = b.id
                    JOIN country_rule cr ON cr.country_code = 'VN' AND cr.direction = b.booking_type
                    JOIN document_requirement dr ON dr.country_rule_id = cr.id
                    JOIN document_type dt ON dt.id = dr.document_type_id
                    WHERE b.id = ? AND dr.gate_location_id IS NULL
                      AND cr.valid_from <= b.requested_pickup_date AND (cr.valid_to IS NULL OR cr.valid_to >= b.requested_pickup_date)
                    RETURNING id, document_type_id
                )
                SELECT a.id FROM approved a JOIN document_type dt ON dt.id = a.document_type_id WHERE dt.code = ?
                """, Long.class, booking, code);
    }

    private long startAndOpenPickup() throws Exception {
        acknowledge(driver);
        acknowledge(escort);
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/arrive").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/depart").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isOk());
        long pickup = handoverId(handover(escort, stops.get(1), "PICKUP").andExpect(status().isCreated()));
        scan(escort, pickup, chip).andExpect(status().isOk());
        return pickup;
    }

    private ResultActions revokeDocument(long documentId, String token, String reason) throws Exception {
        return mvc.perform(post("/bookings/{id}/documents/{documentId}/revoke", booking, documentId)
                .header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(java.util.Map.of("reason", reason))));
    }

    private List<Long> documentNoticeRecipients(String message, String link) {
        return jdbc.queryForList("""
                SELECT r.user_id FROM notification n JOIN notification_recipient r ON r.notification_id = n.id
                WHERE n.booking_id = ? AND n.title = 'Giấy tờ cần bổ sung' AND n.message = ? AND n.link_url = ?
                """, Long.class, booking, message, link);
    }

    @Test
    void tripDoesNotStartWhileInsuranceIsNotConfirmed() throws Exception {
        makeLocal();
        acknowledge(driver);
        acknowledge(escort);
        entityManager.flush();
        jdbc.update("UPDATE horse_insurance SET status = 'DRAFT', confirmed_by = NULL, confirmed_at = NULL WHERE booking_horse_id = (SELECT booking_horse_id FROM horse_leg WHERE id = ?)", leg);
        entityManager.clear();
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Bảo hiểm của")));
    }

    @Test
    void crewUploadsTheSignedRecordAndHandlesOriginalPapers() throws Exception {
        makeLocal();
        // Hộ chiếu ngựa đã duyệt: giấy gốc khách giao lúc nhận ngựa, trả lại lúc giao
        long passport = jdbc.queryForObject("""
                INSERT INTO document (document_type_id, document_requirement_id, file_url, file_name, uploaded_by, status, reviewed_by, reviewed_at, horse_id)
                SELECT dt.id, (SELECT dr.id FROM document_requirement dr WHERE dr.document_type_id = dt.id ORDER BY dr.id LIMIT 1),
                       'test://passport/' || md5(random()::text), 'passport.pdf', u.id, 'APPROVED', u.id, now(),
                       (SELECT horse_id FROM booking_horse WHERE booking_id = ?)
                FROM document_type dt, (SELECT min(id) AS id FROM app_user) u WHERE dt.code = 'HORSE_PASSPORT' RETURNING id""", Long.class, booking);
        var uploader = org.mockito.Mockito.mock(com.cloudinary.Uploader.class);
        org.mockito.Mockito.when(cloudinary.uploader()).thenReturn(uploader);
        org.mockito.Mockito.when(uploader.upload(org.mockito.ArgumentMatchers.any(byte[].class), org.mockito.ArgumentMatchers.anyMap()))
                .thenAnswer(call -> java.util.Map.of("secure_url", "https://cdn.example/"
                        + call.<java.util.Map<?, ?>>getArgument(1).get("public_id") + ".jpg"));
        acknowledge(driver);
        acknowledge(escort);
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/arrive").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/depart").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isOk());
        long pickup = rawHandoverId(handover(escort, stops.get(1), "PICKUP").andExpect(status().isCreated()));
        scan(escort, pickup, chip).andExpect(status().isOk());

        mvc.perform(get("/handovers/{id}/papers", pickup).header(HttpHeaders.AUTHORIZATION, escort)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].documentId").value(passport)).andExpect(jsonPath("$.data[0].direction").doesNotExist());
        paper(pickup, passport).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].direction").value("RECEIVED"));
        var photo = new org.springframework.mock.web.MockMultipartFile("file", "horse.jpg", "image/jpeg", new byte[] {1, 2, 3});
        uploadPhoto(pickup, photo).andExpect(status().isOk());
        uploadPhoto(pickup, photo).andExpect(status().isOk());
        entityManager.flush();
        assertThat(jdbc.queryForList("""
                SELECT file_url FROM document WHERE handover_id = ? AND evidence_horse_leg_id = ? AND file_name = 'horse.jpg'
                """, String.class, pickup, leg)).hasSize(2).doesNotHaveDuplicates();
        act(escort, "/handovers/" + pickup + "/complete").andExpect(status().isConflict());
        uploadRecord(pickup).andExpect(status().isOk()).andExpect(jsonPath("$.data.handovers[0].recordAttached").value(true));
        act(escort, "/handovers/" + pickup + "/complete").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isOk());

        act(driver, "/trip-stops/" + stops.get(2) + "/arrive").andExpect(status().isOk());
        long delivery = rawHandoverId(handover(escort, stops.get(2), "DELIVERY").andExpect(status().isCreated()));
        scan(escort, delivery, chip).andExpect(status().isOk());
        paper(delivery, passport).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].direction").value("RETURNED"));
        uploadPhoto(delivery, photo).andExpect(status().isOk());
        uploadRecord(delivery).andExpect(status().isOk());
        act(escort, "/handovers/" + delivery + "/complete").andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT direction FROM handover_document WHERE document_id = ? ORDER BY handover_id", String.class, passport))
                .containsExactly("RECEIVED", "RETURNED");
        // Người ngoài tổ lái không tải biên bản được
        String stranger = token(testData.staff(testData.unique("exec.paper.stranger"), "ESCORT"), "ESCORT");
        mvc.perform(get("/handovers/{id}/papers", delivery).header(HttpHeaders.AUTHORIZATION, stranger)).andExpect(status().isForbidden());
    }

    private ResultActions paper(long handoverId, long documentId) throws Exception {
        return mvc.perform(post("/handovers/{id}/papers", handoverId).header(HttpHeaders.AUTHORIZATION, escort)
                .contentType(MediaType.APPLICATION_JSON).content("{\"documentId\":%d,\"originalMissing\":false}".formatted(documentId)));
    }

    private ResultActions uploadPhoto(long handoverId, org.springframework.mock.web.MockMultipartFile file) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/handovers/{id}/horses/{horseLegId}/photos", handoverId, leg)
                .file(file).header(HttpHeaders.AUTHORIZATION, escort));
    }

    private ResultActions uploadRecord(long handoverId) throws Exception {
        var file = new org.springframework.mock.web.MockMultipartFile("file", "bien-ban.jpg", "image/jpeg", new byte[] {1, 2, 3});
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/handovers/{id}/record", handoverId)
                .file(file).header(HttpHeaders.AUTHORIZATION, escort));
    }

    // Lần giao nhận vừa mở, chưa có biên bản
    private static long rawHandoverId(ResultActions result) throws Exception {
        List<Number> ids = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.handovers[*].id");
        return ids.getLast().longValue();
    }

    @Test
    void escortIsReplacedMidwayOnlyAfterTheNewOneSignsTheShift() throws Exception {
        makeLocal();
        acknowledge(driver);
        acknowledge(escort);
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/arrive").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/depart").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isOk());
        long pickup = handoverId(handover(escort, stops.get(1), "PICKUP").andExpect(status().isCreated()));
        scan(escort, pickup, chip).andExpect(status().isOk());
        act(escort, "/handovers/" + pickup + "/complete").andExpect(status().isOk());

        mvc.perform(get("/crew/vehicle-trips/{id}", trip).header(HttpHeaders.AUTHORIZATION, escort))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.shiftHandoverStopIds").isEmpty());
        handover(escort, stops.get(1), "SHIFT").andExpect(status().isConflict());
        long redundantShift = jdbc.queryForObject("""
                INSERT INTO handover (handover_type, trip_stop_id, handled_by, status)
                SELECT 'SHIFT', trip_stop_id, handled_by, 'PENDING' FROM handover WHERE id = ?
                RETURNING id
                """, Long.class, pickup);
        mvc.perform(get("/crew/vehicle-trips/{id}/departure-readiness", trip).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.waitingForEscort").value(true));
        act(escort, "/handovers/" + redundantShift + "/complete").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handovers[1].status").value("FAILED"))
                .andExpect(jsonPath("$.data.shiftHandoverStopIds").isEmpty());
        mvc.perform(get("/crew/vehicle-trips/{id}/departure-readiness", trip).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.waitingForEscort").value(false));

        // Lượt đang chạy chỉ thêm được hộ tống thay ca, không cần bắt đầu ở nơi lượt trước kết thúc
        Staff relief = testData.staff(testData.unique("exec.relief"), "ESCORT");
        Staff otherDriver = testData.staff(testData.unique("exec.driver.relief"), "DRIVER");
        crewAssign(otherDriver, "DRIVER").andExpect(status().isConflict());
        crewAssign(relief, "ESCORT").andExpect(status().isCreated());
        String reliefToken = token(relief, "ESCORT");

        // Quy tắc #38: hộ tống đang giữ ngựa thì chưa gỡ được
        long escortAssignment = jdbc.queryForObject("SELECT ca.id FROM crew_assignment ca JOIN staff s ON s.user_id = ca.staff_id "
                + "JOIN app_user u ON u.id = s.user_id WHERE ca.vehicle_trip_id = ? AND ca.crew_role = 'ESCORT' AND u.username <> ? ORDER BY ca.id LIMIT 1",
                Long.class, trip, relief.getUser().getUsername());
        act(coordinator, "/crew-assignments/" + escortAssignment + "/release").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Nhân viên exec.escort")));

        // Tài xế không nhận bàn giao ca; hộ tống mới quét chip và ký, ngựa vẫn trên xe
        handover(driver, stops.get(1), "SHIFT").andExpect(status().isForbidden());
        mvc.perform(get("/crew/vehicle-trips/{id}", trip).header(HttpHeaders.AUTHORIZATION, reliefToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.shiftHandoverStopIds[0]").value(stops.get(1).intValue()));
        long shift = handoverId(handover(reliefToken, stops.get(1), "SHIFT").andExpect(status().isCreated()));
        handover(reliefToken, stops.get(1), "SHIFT").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.handovers[2].id").value((int) shift));
        scan(escort, shift, chip).andExpect(status().isForbidden());
        scan(reliefToken, shift, chip).andExpect(status().isOk());
        act(reliefToken, "/handovers/" + shift + "/complete").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handovers[2].horses[0].microchipMatched").value(true))
                .andExpect(jsonPath("$.data.shiftHandoverStopIds").isEmpty());
        handover(reliefToken, stops.get(1), "SHIFT").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, leg)).isEqualTo("IN_PROGRESS");

        act(coordinator, "/crew-assignments/" + escortAssignment + "/release").andExpect(status().isOk());
        // Hộ tống cũ đã rời ca; người mới tiếp tục giao ngựa
        act(driver, "/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(2) + "/arrive").andExpect(status().isOk());
        long delivery = handoverId(handover(reliefToken, stops.get(2), "DELIVERY").andExpect(status().isCreated()));
        scan(reliefToken, delivery, chip).andExpect(status().isOk());
        act(reliefToken, "/handovers/" + delivery + "/complete").andExpect(status().isOk());
        assertThat(bookingStatus()).isEqualTo("DELIVERED");
    }

    private ResultActions crewAssign(Staff member, String role) throws Exception {
        return mvc.perform(post("/vehicle-trips/{id}/crew-assignments", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":%d,\"crewRole\":\"%s\"}".formatted(member.getUserId(), role)));
    }

    // Sửa thẳng DB rồi bỏ bộ đệm của JPA để service đọc lại
    private void makeLocal() {
        entityManager.flush();
        jdbc.update("UPDATE booking SET booking_type = 'LOCAL' WHERE id = ?", booking);
        entityManager.clear();
    }

    private void acknowledge(String token) throws Exception {
        long assignment = ((Number) JsonPath.read(content(get("/crew-assignments/mine"), token), "$.data[0].id")).longValue();
        act(token, "/crew-assignments/" + assignment + "/acknowledge").andExpect(status().isOk());
    }

    private String bookingStatus() {
        return jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, booking);
    }

    private ResultActions act(String token, String path) throws Exception {
        if (path.endsWith("/start")) {
            return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"earlyReason\":\"Đã sẵn sàng xuất phát sớm\"}"));
        }
        return mvc.perform(HandoverEvidenceFixtures.complete(mvc, jdbc, jwtUtils, path).header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions handover(String token, long stopId, String type) throws Exception {
        return mvc.perform(post("/trip-stops/{id}/handovers", stopId).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"handoverType\":\"%s\"}".formatted(type)));
    }

    private ResultActions scan(String token, long handoverId, String microchip) throws Exception {
        return mvc.perform(post("/handovers/{id}/horses", handoverId).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"horseLegId\":%d,\"conditionNote\":\"Khỏe\",\"microchip\":\"%s\"}".formatted(leg, microchip)));
    }

    private ResultActions chipEntry(long handoverId, String entryMethod, String manualReason) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("horseLegId", leg);
        body.put("microchip", chip);
        body.put("conditionNote", "Khỏe");
        body.put("entryMethod", entryMethod);
        if (manualReason != null) body.put("manualReason", manualReason);
        return mvc.perform(post("/handovers/{id}/horses", handoverId).header(HttpHeaders.AUTHORIZATION, escort)
                .contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(body)));
    }

    private ResultActions refuse(String token, long handoverId, long horseLegId, String reason) throws Exception {
        var uploader = org.mockito.Mockito.mock(com.cloudinary.Uploader.class);
        org.mockito.Mockito.when(cloudinary.uploader()).thenReturn(uploader);
        org.mockito.Mockito.when(uploader.upload(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> java.util.Map.of("secure_url", "https://cdn.example/pickup-miss-" + System.nanoTime() + ".jpg"));
        var request = new org.springframework.mock.web.MockMultipartFile("request", "", "application/json",
                "{\"horseLegId\":%d,\"reason\":\"%s\",\"reasonCode\":\"CHIP_MISMATCH\"}".formatted(horseLegId, reason)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var photo = new org.springframework.mock.web.MockMultipartFile("photo", "pickup.jpg", "image/jpeg", new byte[] {1, 2, 3});
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/handovers/{id}/refusals", handoverId)
                .file(request).file(photo).header(HttpHeaders.AUTHORIZATION, token));
    }

    private String content(org.springframework.test.web.servlet.RequestBuilder request, String token) throws Exception {
        var builder = (org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) request;
        return mvc.perform(builder.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    // Lần giao nhận vừa mở, kèm biên bản đã ký để các bước sau hoàn tất được (ca thiếu biên bản kiểm riêng)
    private long handoverId(ResultActions result) throws Exception {
        List<Number> ids = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.handovers[*].id");
        signedRecord(ids.getLast().longValue());
        return ids.getLast().longValue();
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private static String stop(String key, long id, String type, Instant base, int minutes) {
        String at = base.plusSeconds(60L * minutes).toString();
        String leave = base.plusSeconds(60L * (minutes + 30)).toString();
        return "{\"%s\":%d,\"stopType\":\"%s\",\"plannedArrivalAt\":\"%s\",\"plannedDepartureAt\":\"%s\"}".formatted(key, id, type, at, leave);
    }

    private String token(Staff staff, String role) {
        return "Bearer " + jwtUtils.generateToken(staff.getUser().getUsername(), List.of(role));
    }

    // Biên bản đã ký (giả lập ảnh đã tải lên): bắt buộc trước khi hoàn tất nhận hoặc giao ngựa
    private void signedRecord(long handoverId) {
        jdbc.update("INSERT INTO document (document_type_id, file_url, file_name, uploaded_by, status, handover_id) "
                + "SELECT id, 'test://record/' || md5(random()::text), 'record.jpg', (SELECT min(id) FROM app_user), 'UPLOADED', ? FROM document_type WHERE code = 'HANDOVER_RECORD'", handoverId);
    }

    // Thông báo khách nhận được trên đơn (B6), theo thứ tự gửi
    private List<String> customerNotices(long bookingId) {
        entityManager.flush();
        return jdbc.queryForList("SELECT n.title FROM notification n JOIN notification_recipient r ON r.notification_id = n.id "
                + "JOIN booking b ON b.id = n.booking_id WHERE n.booking_id = ? AND r.user_id = b.customer_id ORDER BY n.id", String.class, bookingId);
    }
}
