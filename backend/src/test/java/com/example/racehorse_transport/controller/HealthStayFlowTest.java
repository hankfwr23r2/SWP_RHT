package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// B3: ghi sức khỏe ở trạm nghỉ, rời trạm phải nghỉ đủ (#34), tập kết ở hub sau khi giao vào hub
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class HealthStayFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private LocationRepository locations;
    @Autowired private EntityManager entityManager;

    private String coordinator;
    private String escort;
    private String driver;
    private Staff coordinatorStaff;
    private long booking;
    private long pickup;
    private long delivery;
    private long line;
    private String chip;
    private long hcm;
    private Instant start;

    @BeforeEach
    void setUp() {
        coordinatorStaff = testData.staff(testData.unique("health.coordinator"), "COORDINATOR");
        coordinator = token(coordinatorStaff, "COORDINATOR");
        booking = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DIRECT-001'", Long.class);
        pickup = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'PICKUP'", Long.class, booking);
        delivery = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'DELIVERY'", Long.class, booking);
        line = jdbc.queryForObject("SELECT id FROM booking_horse WHERE booking_id = ?", Long.class, booking);
        chip = jdbc.queryForObject("SELECT h.microchip FROM booking_horse bh JOIN horse h ON h.id = bh.horse_id WHERE bh.id = ?", String.class, line);
        hcm = locations.findByCode("HUB-HCM").orElseThrow().getId();
        start = jdbc.queryForObject("SELECT requested_pickup_date FROM booking WHERE id = ?", java.sql.Date.class, booking)
                .toLocalDate().atTime(1, 0).toInstant(ZoneOffset.UTC);
        entityManager.flush();
        jdbc.update("UPDATE booking SET booking_type = 'LOCAL' WHERE id = ?", booking);
        entityManager.clear();
    }

    @Test
    void restStopNeedsRestButNotAFeedLog() throws Exception {
        long rest = locations.findByCode("REST-PHANTHIET").orElseThrow().getId();
        List<Long> stops = runToPickupAndLeave("DIRECT", List.of(
                stop("locationId", hcm, "HUB", 0), stop("bookingAddressId", pickup, "PICKUP", 60), stop("locationId", rest, "REST", 120),
                stop("bookingAddressId", delivery, "DELIVERY", 240), stop("locationId", hcm, "HUB", 480)), 2);
        drive("/trip-stops/" + stops.get(2) + "/arrive").andExpect(status().isOk());

        drive("/trip-stops/" + stops.get(2) + "/depart").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa nghỉ đủ 30 phút. Rời sớm phải ghi lý do."));
        log(stops.get(2), null, "NORMAL", null, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.placeName").value(locations.findById(rest).orElseThrow().getName()));
        drive("/trip-stops/" + stops.get(2) + "/depart").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa nghỉ đủ 30 phút. Rời sớm phải ghi lý do."));
        mvc.perform(post("/trip-stops/{id}/depart", stops.get(2)).header(HttpHeaders.AUTHORIZATION, driver).contentType(MediaType.APPLICATION_JSON)
                .content("{\"earlyReason\":\"Kịp giờ giao theo hẹn\"}")).andExpect(status().isOk());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'LEAVE_REST_STOP_EARLY' AND target_id = ?", Integer.class, stops.get(2)))
                .isEqualTo(1);
        assertThat(earlyOvernightNotices()).isEmpty();
    }

    @Test
    void completesATripTaskFromTheAssignedEscortHealthLog() throws Exception {
        long rest = locations.findByCode("REST-PHANTHIET").orElseThrow().getId();
        List<Long> stops = runToPickupAndLeave("DIRECT", List.of(
                stop("locationId", hcm, "HUB", 0), stop("bookingAddressId", pickup, "PICKUP", 60), stop("locationId", rest, "REST", 120),
                stop("bookingAddressId", delivery, "DELIVERY", 240), stop("locationId", hcm, "HUB", 480)), 2);
        long stopId = stops.get(2);
        drive("/trip-stops/" + stopId + "/arrive").andExpect(status().isOk());
        long tripId = jdbc.queryForObject("SELECT vehicle_trip_id FROM trip_stop WHERE id = ?", Long.class, stopId);
        long escortId = jdbc.queryForObject("""
                SELECT staff_id FROM crew_assignment WHERE vehicle_trip_id = ? AND crew_role = 'ESCORT'
                """, Long.class, tripId);
        Staff outsider = testData.staff(testData.unique("health.outsider"), "ESCORT");
        createHealthCheckTask(tripId, outsider.getUserId()).andExpect(status().isBadRequest());
        String assigned = createHealthCheckTask(tripId, escortId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Number taskId = JsonPath.read(assigned, "$.data.id");
        log(stopId, null, "NORMAL", "NORMAL", "NORMAL").andExpect(status().isCreated());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM health_check_task WHERE id = ?", String.class, taskId.longValue()))
                .isEqualTo("DONE");
    }

    private ResultActions createHealthCheckTask(long tripId, long escortId) throws Exception {
        return mvc.perform(post("/health-check-tasks").header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"bookingHorseId":%d,"vehicleTripId":%d,"assignedTo":%d,"checkType":"ROUTINE","dueAt":"%s"}
                        """.formatted(line, tripId, escortId, Instant.now().plusSeconds(3600))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"REST", "VITAL_CHECK"})
    void leavingAnOvernightRestEarlyRequiresAReasonAndAlertsCoordinators(String stopType) throws Exception {
        long stopId = arriveAtOvernightStop(stopType, 480, 7);
        drive("/trip-stops/" + stopId + "/depart").andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.earlyReason").value("Chưa nghỉ đủ 8 giờ. Rời sớm phải ghi lý do."));
        assertThat(earlyOvernightNotices()).isEmpty();

        String reason = "Di chuyển khỏi trạm do ngập đường";
        mvc.perform(post("/trip-stops/{id}/depart", stopId).header(HttpHeaders.AUTHORIZATION, driver)
                .contentType(MediaType.APPLICATION_JSON).content("{\"earlyReason\":\"%s\"}".formatted(reason)))
                .andExpect(status().isOk());

        entityManager.flush();
        String tripCode = jdbc.queryForObject("""
                SELECT t.code FROM vehicle_trip t JOIN trip_stop s ON s.vehicle_trip_id = t.id WHERE s.id = ?
                """, String.class, stopId);
        assertThat(earlyOvernightNotices()).singleElement().asString().contains(tripCode, reason);
        assertThat(jdbc.queryForObject("""
                SELECT new_data->>'reason' FROM audit_log WHERE action = 'LEAVE_REST_STOP_EARLY' AND target_id = ?
                """, String.class, stopId)).isEqualTo(reason);
    }

    @Test
    void overnightVitalCheckCanDepartAfterRestingTheConfiguredHours() throws Exception {
        long stopId = arriveAtOvernightStop("VITAL_CHECK", 510, 8);

        drive("/trip-stops/" + stopId + "/depart").andExpect(status().isOk());
        assertThat(earlyOvernightNotices()).isEmpty();
    }

    private long arriveAtOvernightStop(String type, int plannedRestMinutes, int actualRestHours) throws Exception {
        // Dùng ngưỡng 8 giờ để lượt chạy thẳng vẫn nằm trong giới hạn ngồi xe.
        jdbc.update("UPDATE system_config SET config_value = '8' WHERE config_key = 'OVERNIGHT_REST_HOURS'");
        long rest = locations.findByCode("REST-PHANTHIET").orElseThrow().getId();
        List<Long> stops = runToPickupAndLeave("DIRECT", List.of(
                stop("locationId", hcm, "HUB", 0), stop("bookingAddressId", pickup, "PICKUP", 60),
                stop("locationId", rest, type, 120, plannedRestMinutes),
                stop("bookingAddressId", delivery, "DELIVERY", 660), stop("locationId", hcm, "HUB", 720)), 2);
        long stopId = stops.get(2);
        drive("/trip-stops/" + stopId + "/arrive").andExpect(status().isOk());
        log(stopId, null, "NORMAL", "NORMAL", "NORMAL").andExpect(status().isCreated());
        entityManager.flush();
        jdbc.update("UPDATE trip_stop SET actual_arrival_at = clock_timestamp() - make_interval(hours => ?) WHERE id = ?",
                actualRestHours, stopId);
        entityManager.clear();
        return stopId;
    }

    private List<String> earlyOvernightNotices() {
        entityManager.flush();
        return jdbc.queryForList("""
                SELECT n.message FROM notification_recipient r JOIN notification n ON n.id = r.notification_id
                WHERE r.user_id = ? AND n.title = 'Rời điểm nghỉ đêm sớm'
                """, String.class, coordinatorStaff.getUserId());
    }

    @Test
    void handingIntoHubOpensAnAssemblyStayWhereHealthIsLogged() throws Exception {
        List<Long> stops = runToPickupAndLeave("PICKUP", List.of(
                stop("locationId", hcm, "HUB", 0), stop("bookingAddressId", pickup, "PICKUP", 60), stop("locationId", hcm, "HUB", 240)), 2);
        drive("/trip-stops/" + stops.get(2) + "/arrive").andExpect(status().isOk());
        long hubIn = handover(stops.get(2), "HUB_IN");
        scanAndComplete(hubIn);

        String stays = mvc.perform(get("/horse-stays").param("locationId", String.valueOf(hcm)).header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.bookingHorseId == " + line + ")].stayType").value("ASSEMBLY"))
                .andReturn().getResponse().getContentAsString();
        long stay = ((Number) ((List<?>) JsonPath.read(stays, "$.data[?(@.bookingHorseId == " + line + ")].id")).getFirst()).longValue();

        log(null, stay, "NORMAL", "NORMAL", "NORMAL").andExpect(status().isCreated()).andExpect(jsonPath("$.data.horseStayId").value(stay));
        log(stops.get(2), stay, "NORMAL", "NORMAL", "NORMAL").andExpect(status().isBadRequest());
        long trip = jdbc.queryForObject("SELECT vehicle_trip_id FROM trip_stop WHERE id = ?", Long.class, stops.get(2));
        TripReturnPhotoFixtures.attach(jdbc, trip);
        drive("/vehicle-trips/" + trip + "/complete")
                .andExpect(status().isOk());
    }

    @Test
    void stayHealthRequiresCurrentCustodianOrCoordinatorWithReportSource() throws Exception {
        List<Long> stops = runToPickupAndLeave("PICKUP", List.of(
                stop("locationId", hcm, "HUB", 0), stop("bookingAddressId", pickup, "PICKUP", 60),
                stop("locationId", hcm, "HUB", 240)), 2);
        long hubStop = stops.getLast();
        drive("/trip-stops/" + hubStop + "/arrive").andExpect(status().isOk());
        long hubIn = handover(hubStop, "HUB_IN");
        scanAndComplete(hubIn);
        long stay = jdbc.queryForObject("SELECT id FROM horse_stay WHERE booking_horse_id = ? AND actual_end_at IS NULL",
                Long.class, line);
        String body = logBody(null, stay, "NORMAL", "NORMAL", "NORMAL");
        Staff driverStaff = testData.staff(testData.unique("health.relief"), "DRIVER");
        String driver = token(driverStaff, "DRIVER");
        String manager = token(testData.staff(testData.unique("health.manager"), "MANAGER"), "MANAGER");
        String lineFilter = "$.data[?(@.bookingHorseId == " + line + ")]";

        postHealth(driver, body).andExpect(status().isForbidden());
        postHealth(manager, body).andExpect(status().isForbidden());
        postHealth(coordinator, logBody(hubStop, null, "NORMAL", null, null)).andExpect(status().isForbidden());
        postHealth(coordinator, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.reportedBy").exists());
        mvc.perform(get("/horse-stays/mine").header(HttpHeaders.AUTHORIZATION, escort))
                .andExpect(status().isOk()).andExpect(jsonPath(lineFilter + ".id").value((int) stay));
        mvc.perform(get("/horse-stays/mine").header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk()).andExpect(jsonPath(lineFilter).isEmpty());
        mvc.perform(get("/horse-stays/mine").header(HttpHeaders.AUTHORIZATION, coordinator)).andExpect(status().isForbidden());

        postHealth(escort, body).andExpect(status().isCreated());
        entityManager.flush();
        jdbc.update("UPDATE handover SET handed_at = clock_timestamp() - interval '13 hours', handled_by = ? WHERE id = ?",
                driverStaff.getUserId(), hubIn);
        jdbc.update("UPDATE handover SET handed_at = clock_timestamp() - interval '14 hours' WHERE trip_stop_id = ?", stops.get(1));
        jdbc.update("UPDATE horse_health_log SET recorded_at = clock_timestamp() - interval '13 hours' WHERE horse_stay_id = ?", stay);
        entityManager.clear();
        postHealth(escort, body).andExpect(status().isForbidden());
        mvc.perform(get("/horse-stays/mine").header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk()).andExpect(jsonPath(lineFilter + ".id").value((int) stay));
        mvc.perform(get("/custody-board").header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andExpect(jsonPath(lineFilter + ".careOverdue").value(true));
        postHealth(driver, body).andExpect(status().isForbidden());
        postHealth(coordinator, withReport(logBody(null, stay, "CRITICAL", "NORMAL", "NORMAL"), """
                "reportedBy":"Nhân viên chuồng"
                """)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.note").value("Người báo: Nhân viên chuồng"));
        mvc.perform(get("/custody-board").header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andExpect(jsonPath(lineFilter + ".careOverdue").value(false));
        entityManager.flush();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM notification_recipient r JOIN notification n ON n.id = r.notification_id
                WHERE r.user_id = ? AND n.title = 'Sức khỏe ngựa Nguy cấp'
                """, Integer.class, coordinatorStaff.getUserId())).isEqualTo(1);

        jdbc.update("UPDATE horse_stay SET actual_end_at = clock_timestamp() WHERE id = ?", stay);
        entityManager.clear();
        postHealth(coordinator, withReport(body, """
                "reportedBy":"Nhân viên chuồng"
                """)).andExpect(status().isConflict());
        mvc.perform(get("/horse-stays/mine").header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isOk()).andExpect(jsonPath(lineFilter).isEmpty());
    }

    private ResultActions postHealth(String token, String body) throws Exception {
        return mvc.perform(post("/health-logs").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String withReport(String body, String report) {
        return body.substring(0, body.length() - 1) + "," + report + "}";
    }

    @Test
    void onlyCrewLogsAtAStopWhereTheHorseIsOnBoard() throws Exception {
        List<Long> stops = runToPickupAndLeave("DIRECT", List.of(
                stop("locationId", hcm, "HUB", 0), stop("bookingAddressId", pickup, "PICKUP", 60),
                stop("bookingAddressId", delivery, "DELIVERY", 240), stop("locationId", hcm, "HUB", 480)), 2);
        // Xe đã rời điểm đón
        log(stops.get(1), null, "NORMAL", "NORMAL", "NORMAL").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chỉ ghi khi xe đang ở điểm dừng này."));
        String stranger = token(testData.staff(testData.unique("health.stranger"), "ESCORT"), "ESCORT");
        drive("/trip-stops/" + stops.get(2) + "/arrive").andExpect(status().isOk());
        mvc.perform(post("/health-logs").header(HttpHeaders.AUTHORIZATION, stranger).contentType(MediaType.APPLICATION_JSON)
                        .content(logBody(stops.get(2), null, "NORMAL", "NORMAL", "NORMAL")))
                .andExpect(status().isForbidden());
    }

    // Lập lượt, phân công tài xế và hộ tống, xếp ngựa, xuất phát, đi tới điểm đón, nhận ngựa rồi rời điểm đón
    @Test
    void overnightHorsesGoToThePartnerStableAndComeBackBeforeTheVehicleLeaves() throws Exception {
        long rest = locations.findByCode("REST-PHANTHIET").orElseThrow().getId();
        List<Long> stops = runToPickupAndLeave("DIRECT", List.of(
                stop("locationId", hcm, "HUB", 0), stop("bookingAddressId", pickup, "PICKUP", 60), stop("locationId", rest, "REST", 120),
                stop("bookingAddressId", delivery, "DELIVERY", 240), stop("locationId", hcm, "HUB", 480)), 2);
        drive("/trip-stops/" + stops.get(2) + "/arrive").andExpect(status().isOk());
        long stable = jdbc.queryForObject("SELECT id FROM party WHERE display_name = 'Chuồng gửi ngựa Đà Nẵng (mẫu)'", Long.class);
        long vet = jdbc.queryForObject("SELECT id FROM party WHERE display_name = 'Phòng khám thú y ngựa (mẫu)'", Long.class);
        mvc.perform(get("/stable-partners").header(HttpHeaders.AUTHORIZATION, escort)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + stable + ")]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.id == " + vet + ")]").isEmpty());

        // Gửi chuồng: phải chọn đối tác có vai trò chuồng gửi
        stayHandover(stops.get(2), "STAY_IN", null).andExpect(status().isBadRequest());
        stayHandover(stops.get(2), "STAY_IN", vet).andExpect(status().isBadRequest());
        long in = handoverIdOf(stayHandover(stops.get(2), "STAY_IN", stable).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.handovers[-1:].partyName").value(org.hamcrest.Matchers.hasItem("Chuồng gửi ngựa Đà Nẵng (mẫu)"))));
        scanOnly(in);
        act("/handovers/" + in + "/complete").andExpect(status().isOk());
        var stay = jdbc.queryForMap("SELECT id, stay_type, operator_party_id, actual_end_at FROM horse_stay WHERE booking_horse_id = ? ORDER BY id DESC LIMIT 1", line);
        assertThat(stay.get("stay_type")).isEqualTo("OVERNIGHT");
        assertThat(((Number) stay.get("operator_party_id")).longValue()).isEqualTo(stable);
        assertThat(stay.get("actual_end_at")).isNull();
        // Ngựa vẫn đang trên chặng, chỉ tạm xuống xe; hành trình cho thấy đối tác đang giữ
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE booking_horse_id = ? AND status <> 'CANCELLED' ORDER BY sequence_number DESC LIMIT 1", String.class, line))
                .isEqualTo("IN_PROGRESS");
        mvc.perform(get("/bookings/{id}/journey", booking).header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(jsonPath("$.data.horses[0].holder").value("PARTY"));

        long stayId = ((Number) stay.get("id")).longValue();
        postHealth(coordinator, withReport(logBody(null, stayId, "NORMAL", "NORMAL", "NORMAL"), """
                "reportedBy":"Nhân viên chuồng"
                """)).andExpect(status().isCreated()).andExpect(jsonPath("$.data.horseStayId").value(stayId));

        // Chưa nhận lại thì chưa rời trạm
        log(stops.get(2), null, "NORMAL", "NORMAL", "NORMAL").andExpect(status().isCreated());
        drive("/trip-stops/" + stops.get(2) + "/depart").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Còn ngựa đang gửi chuồng qua đêm: Sao Mai. Nhận lại trước khi rời trạm."));
        long out = handoverIdOf(stayHandover(stops.get(2), "STAY_OUT", null).andExpect(status().isCreated()));
        scanOnly(out);
        act("/handovers/" + out + "/complete").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT actual_end_at IS NOT NULL FROM horse_stay WHERE booking_horse_id = ? AND stay_type = 'OVERNIGHT'", Boolean.class, line)).isTrue();
        mvc.perform(post("/trip-stops/{id}/depart", stops.get(2)).header(HttpHeaders.AUTHORIZATION, driver).contentType(MediaType.APPLICATION_JSON)
                .content("{\"earlyReason\":\"Nghỉ đêm xong\"}")).andExpect(status().isOk());
    }

    private ResultActions stayHandover(long stopId, String type, Long partyId) throws Exception {
        return mvc.perform(post("/trip-stops/{id}/handovers", stopId).header(HttpHeaders.AUTHORIZATION, escort)
                .contentType(MediaType.APPLICATION_JSON).content("{\"handoverType\":\"%s\",\"partyId\":%s}".formatted(type, partyId)));
    }

    private static long handoverIdOf(ResultActions result) throws Exception {
        List<Number> ids = com.jayway.jsonpath.JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.handovers[*].id");
        return ids.getLast().longValue();
    }

    private void scanOnly(long handoverId) throws Exception {
        long leg = jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND status <> 'CANCELLED' ORDER BY sequence_number DESC LIMIT 1", Long.class, line);
        mvc.perform(post("/handovers/{id}/horses", handoverId).header(HttpHeaders.AUTHORIZATION, escort).contentType(MediaType.APPLICATION_JSON)
                .content("{\"horseLegId\":%d,\"conditionNote\":\"Khỏe\",\"microchip\":\"%s\"}".formatted(leg, chip))).andExpect(status().isOk());
    }

    private List<Long> runToPickupAndLeave(String type, List<String> stopBodies, int alightIndex) throws Exception {
        Staff driverStaff = testData.staff(testData.unique("health.driver"), "DRIVER");
        Staff escortStaff = testData.staff(testData.unique("health.escort"), "ESCORT");
        escort = token(escortStaff, "ESCORT");
        String body = "{\"tripType\":\"%s\",\"vehicleId\":%d,\"stops\":[%s]}".formatted(type, testData.vehicle(2, 0).getId(), String.join(",", stopBodies));
        String created = mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long trip = ((Number) JsonPath.read(created, "$.data.id")).longValue();
        List<Long> stops = ((List<Number>) JsonPath.read(created, "$.data.stops[*].id")).stream().map(Number::longValue).toList();
        for (Staff member : List.of(driverStaff, escortStaff)) {
            mvc.perform(post("/vehicle-trips/{id}/crew-assignments", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"staffId\":%d,\"crewRole\":\"%s\"}".formatted(member.getUserId(), member == driverStaff ? "DRIVER" : "ESCORT")))
                    .andExpect(status().isCreated());
        }
        long alight = type.equals("DIRECT") ? stops.get(stops.size() - 2) : stops.getLast();
        mvc.perform(post("/vehicle-trips/{id}/horse-legs", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingHorseId\":%d,\"boardTripStopId\":%d,\"alightTripStopId\":%d}".formatted(line, stops.get(1), alight))).andExpect(status().isCreated());
        driver = token(driverStaff, "DRIVER");
        for (String member : List.of(driver, escort)) {
            String mine = mvc.perform(get("/crew-assignments/mine").header(HttpHeaders.AUTHORIZATION, member)).andReturn().getResponse().getContentAsString();
            mvc.perform(post("/crew-assignments/{id}/acknowledge", ((Number) JsonPath.read(mine, "$.data[0].id")).longValue()).header(HttpHeaders.AUTHORIZATION, member))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/vehicle-trips/{id}/start", trip).header(HttpHeaders.AUTHORIZATION, driver).contentType(MediaType.APPLICATION_JSON)
                .content("{\"earlyReason\":\"Đã sẵn sàng xuất phát sớm\"}")).andExpect(status().isOk());
        drive("/trip-stops/" + stops.get(0) + "/arrive").andExpect(status().isOk());
        drive("/trip-stops/" + stops.get(0) + "/depart").andExpect(status().isOk());
        drive("/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isOk());
        scanAndComplete(handover(stops.get(1), "PICKUP"));
        drive("/trip-stops/" + stops.get(1) + "/depart").andExpect(status().isOk());
        return stops;
    }

    private long handover(long stopId, String type) throws Exception {
        String body = mvc.perform(post("/trip-stops/{id}/handovers", stopId).header(HttpHeaders.AUTHORIZATION, escort)
                .contentType(MediaType.APPLICATION_JSON).content("{\"handoverType\":\"%s\"}".formatted(type)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$.data.handovers[*].id");
        return ids.getLast().longValue();
    }

    private void scanAndComplete(long handoverId) throws Exception {
        long leg = jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND status <> 'CANCELLED' ORDER BY sequence_number DESC LIMIT 1", Long.class, line);
        mvc.perform(post("/handovers/{id}/horses", handoverId).header(HttpHeaders.AUTHORIZATION, escort).contentType(MediaType.APPLICATION_JSON)
                .content("{\"horseLegId\":%d,\"conditionNote\":\"Khỏe\",\"microchip\":\"%s\"}".formatted(leg, chip))).andExpect(status().isOk());
        signedRecord(handoverId);
        act("/handovers/" + handoverId + "/complete").andExpect(status().isOk());
    }

    private ResultActions log(Long stopId, Long stayId, String health, String water, String feed) throws Exception {
        return mvc.perform(post("/health-logs").header(HttpHeaders.AUTHORIZATION, escort).contentType(MediaType.APPLICATION_JSON)
                .content(logBody(stopId, stayId, health, water, feed)));
    }

    private String logBody(Long stopId, Long stayId, String health, String water, String feed) {
        return "{\"bookingHorseId\":%d,\"tripStopId\":%s,\"horseStayId\":%s,\"healthStatus\":\"%s\",\"waterIntake\":%s,\"feedIntake\":%s}".formatted(
                line, stopId, stayId, health, water == null ? null : "\"" + water + "\"", feed == null ? null : "\"" + feed + "\"");
    }

    private ResultActions drive(String path) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, driver));
    }

    private ResultActions act(String path) throws Exception {
        return mvc.perform(HandoverEvidenceFixtures.complete(mvc, jdbc, jwtUtils, path).header(HttpHeaders.AUTHORIZATION, escort));
    }

    private String stop(String key, long id, String type, int minutes) {
        return stop(key, id, type, minutes, 30);
    }

    private String stop(String key, long id, String type, int minutes, int dwellMinutes) {
        String at = start.plusSeconds(60L * minutes).toString();
        String leave = start.plusSeconds(60L * (minutes + dwellMinutes)).toString();
        return "{\"%s\":%d,\"stopType\":\"%s\",\"plannedArrivalAt\":\"%s\",\"plannedDepartureAt\":\"%s\"}".formatted(key, id, type, at, leave);
    }

    private String token(Staff staff, String role) {
        return "Bearer " + jwtUtils.generateToken(staff.getUser().getUsername(), List.of(role), 0);
    }

    // Biên bản đã ký (giả lập ảnh đã tải lên): bắt buộc trước khi hoàn tất nhận hoặc giao ngựa
    private void signedRecord(long handoverId) {
        jdbc.update("INSERT INTO document (document_type_id, file_url, file_name, uploaded_by, status, handover_id) "
                + "SELECT id, 'test://record/' || md5(random()::text), 'record.jpg', (SELECT min(id) FROM app_user), 'UPLOADED', ? FROM document_type WHERE code = 'HANDOVER_RECORD'", handoverId);
    }
}
