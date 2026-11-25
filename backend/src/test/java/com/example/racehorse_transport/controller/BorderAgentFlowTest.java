package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.support.HandoverEvidenceFixtures;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import com.example.racehorse_transport.client.CloudinaryClient;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// B2 (GĐ7.4): chặng đại lý và bàn giao AGENT ở cửa khẩu. Đơn xuất: tổ xe giao cho đại lý, điều phối nhập hộ mốc đại lý giao.
// Đơn nhập: đại lý giao cho tổ xe, chỉ nhận khi đủ giấy (#35), rời cửa khẩu khi đã trả đủ (#32)
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class BorderAgentFlowTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private CloudinaryClient cloudinary;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private LocationRepository locations;
    @Autowired private EntityManager entityManager;

    private String coordinator;
    private String driver;
    private String escort;
    private Staff driverStaff;
    private Staff escortStaff;
    private long booking;
    private long line;
    private String chip;
    private long agent;
    private long gate;

    @BeforeEach
    void setUp() {
        coordinator = token(testData.staff(testData.unique("agent.coordinator"), "COORDINATOR"), "COORDINATOR");
        driverStaff = testData.staff(testData.unique("agent.driver"), "DRIVER");
        escortStaff = testData.staff(testData.unique("agent.escort"), "ESCORT");
        driver = token(driverStaff, "DRIVER");
        escort = token(escortStaff, "ESCORT");
        booking = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-EXPORT-001'", Long.class);
        line = jdbc.queryForObject("SELECT min(id) FROM booking_horse WHERE booking_id = ?", Long.class, booking);
        chip = jdbc.queryForObject("SELECT h.microchip FROM booking_horse bh JOIN horse h ON h.id = bh.horse_id WHERE bh.id = ?", String.class, line);
        agent = jdbc.queryForObject("SELECT id FROM party WHERE display_name = 'Đại lý vận tải Campuchia (mẫu)'", Long.class);
        gate = locations.findByCode("GATE-MOCBAI").orElseThrow().getId();
    }

    @Test
    void exportHorseIsHandedToTheAgentAtTheBorderAndTheAgentDelivers() throws Exception {
        long departure = jdbc.queryForObject("SELECT departure_id FROM booking WHERE id = ?", Long.class, booking);
        long trip = linehaulTrip(departure, null);
        List<Long> stops = stopIds(trip);
        assignLeg(trip, stops.getFirst(), stops.getLast());
        Instant atGate = Instant.parse(JsonPath.read(content(get("/vehicle-trips/" + trip), coordinator), "$.data.stops[2].plannedDepartureAt"));

        // Quy tắc #26: đối tác phải có vai trò đại lý nước đến
        long vet = jdbc.queryForObject("SELECT id FROM party WHERE display_name = 'Phòng khám thú y ngựa (mẫu)'", Long.class);
        agentLeg(vet, null, atGate, atGate.plusSeconds(86_400)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Phòng khám thú y ngựa (mẫu) không có vai trò đại lý")));
        long agentLeg = id(agentLeg(agent, null, atGate, atGate.plusSeconds(86_400)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.legType").value("DELIVERY"))
                .andExpect(jsonPath("$.data.agentName").value("Đại lý vận tải Campuchia (mẫu)")));
        // Đại lý chưa nhận ngựa thì chưa ghi giao được
        act(coordinator, "/agent-legs/" + agentLeg + "/complete").andExpect(status().isConflict());

        // Đơn đã qua lượt đón (đang vận hành)
        entityManager.flush();
        jdbc.update("UPDATE booking SET status = 'IN_OPERATION' WHERE id = ?", booking);
        entityManager.clear();
        driveToTheGate(trip, stops);

        long handover = handoverId(handover(stops.get(2), null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.handovers[1].partyName").value("Đại lý vận tải Campuchia (mẫu)")));
        scan(handover, legOnTrip(trip)).andExpect(status().isOk());
        act(escort, "/handovers/" + handover + "/complete").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Chưa có biên bản giao nhận")));
        attachRecord(handover);
        act(escort, "/handovers/" + handover + "/complete").andExpect(status().isOk());
        assertThat(legStatus(legOnTrip(trip))).isEqualTo("COMPLETED");
        assertThat(legStatus(agentLeg)).isEqualTo("IN_PROGRESS");
        assertThat(customerNotices(booking)).contains("Đã qua cửa khẩu");
        act(driver, "/vehicle-trips/" + trip + "/complete").andExpect(status().isOk());

        // Trang theo dõi có chặng của đại lý để điều phối ghi đã giao
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/custody-board").header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.agentLegId == " + agentLeg + ")]").isNotEmpty());

        mvc.perform(multipart("/agent-legs/{id}/complete", agentLeg).header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Cần ảnh hoặc biên bản đại lý đã giao."));
        // Điều phối nhập hộ mốc đại lý đã giao: con cuối giao xong thì đơn DELIVERED
        act(coordinator, "/agent-legs/" + agentLeg + "/complete").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertThat(bookingStatus()).isEqualTo("DELIVERED");
        assertThat(customerNotices(booking)).contains("Đại lý đã giao ngựa");
    }

    @Test
    void importHorseIsReceivedFromTheAgentOnlyWithCompleteDocuments() throws Exception {
        // Đơn mẫu đổi thành đơn nhập, đi chuyến Mộc Bài về TP.HCM
        java.time.LocalDate pickup = jdbc.queryForObject("SELECT requested_pickup_date FROM booking WHERE id = ?", java.sql.Date.class, booking).toLocalDate();
        long departure = testData.departure("MOCBAI-HCM", pickup.plusDays(1), 9).getId();
        entityManager.flush();
        jdbc.update("UPDATE booking SET booking_type = 'IMPORT', departure_id = ? WHERE id = ?", departure, booking);
        // Đơn nhập: nhận ngựa ở Campuchia, giao ở Việt Nam (đổi chiều hai địa chỉ của đơn mẫu)
        jdbc.update("""
                UPDATE booking_address a SET country_code = b.country_code, province_code = b.province_code, address_line = b.address_line,
                    latitude = b.latitude, longitude = b.longitude
                FROM booking_address b WHERE a.booking_id = ? AND b.booking_id = a.booking_id AND b.id <> a.id""", booking);
        entityManager.clear();
        Instant departAt = jdbc.queryForObject("SELECT planned_departure_at FROM departure WHERE id = ?", java.sql.Timestamp.class, departure).toInstant();

        agentLeg(agent, null, departAt.minusSeconds(86_400), departAt).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Đơn nhập phải chọn cửa khẩu đại lý giao ngựa."));
        long agentLeg = id(agentLeg(agent, gate, departAt.minusSeconds(86_400), departAt).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.legType").value("PICKUP")));
        act(coordinator, "/agent-legs/" + agentLeg + "/start").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));

        long trip = linehaulTrip(departure, gate);
        List<Long> stops = stopIds(trip);
        assignLeg(trip, stops.getFirst(), stops.getLast());
        acknowledge();
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.getFirst() + "/arrive").andExpect(status().isOk());

        long handover = handoverId(handover(stops.getFirst(), agent).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.handovers[0].partyName").value("Đại lý vận tải Campuchia (mẫu)")));
        scan(handover, legOnTrip(trip)).andExpect(status().isOk());
        attachRecord(handover);
        // Quy tắc #35: thiếu giấy (gồm giấy vận chuyển mẫu 14a) thì không nhận, ngựa ở lại với đại lý
        act(escort, "/handovers/" + handover + "/complete").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Đơn DEV-EXPORT-001 còn thiếu giấy bắt buộc")));
        assertThat(legStatus(legOnTrip(trip))).isEqualTo("PLANNED");

        // Đủ giấy (giả lập bằng đơn không cần giấy như các test khác): nhận lên xe, chặng đại lý kết thúc
        entityManager.flush();
        jdbc.update("UPDATE booking SET booking_type = 'LOCAL' WHERE id = ?", booking);
        entityManager.clear();
        act(escort, "/handovers/" + handover + "/complete").andExpect(status().isOk());
        assertThat(legStatus(legOnTrip(trip))).isEqualTo("IN_PROGRESS");
        assertThat(legStatus(agentLeg)).isEqualTo("COMPLETED");
        assertThat(customerNotices(booking)).contains("Đã nhận ngựa ở cửa khẩu");
        // Rời cửa khẩu là rời nơi nhận: đã trả đủ (seed) nên đi được, đơn sang IN_OPERATION
        act(driver, "/trip-stops/" + stops.getFirst() + "/depart").andExpect(status().isOk());
        assertThat(bookingStatus()).isEqualTo("IN_OPERATION");
    }

    @Test
    void importBalanceOverdueAtTheGateAllowsTransportToHub() throws Exception {
        long[] received = receiveImportAtTheGate();
        long trip = received[0];
        long gateStop = received[1];
        long original = received[2];
        // Nhận ở cửa khẩu xong thì lập khoản số dư (seed có sẵn số dư đã trả nên đã bỏ đi)
        assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE booking_id = ? AND payment_type = 'BALANCE'", String.class, booking)).isEqualTo("PENDING");
        entityManager.flush();
        jdbc.update("UPDATE payment SET due_at = now() - interval '1 minute' WHERE booking_id = ? AND payment_type = 'BALANCE'", booking);
        entityManager.clear();
        returnAt(gateStop).andExpect(status().isBadRequest());
        mvc.perform(post("/trip-stops/{id}/depart", gateStop).header(HttpHeaders.AUTHORIZATION, driver)).andExpect(status().isOk());
        assertThat(legStatus(legOnTrip(trip))).isEqualTo("IN_PROGRESS");
        assertThat(bookingStatus()).isEqualTo("CONFIRMED");
        mvc.perform(get("/bookings/{id}/journey", booking).header(HttpHeaders.AUTHORIZATION, coordinator)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.horses[0].holder").value("COMPANY"));
    }

    @Test
    void importHorseRefusedAtTheGateStaysWithTheAgent() throws Exception {
        long[] gate = arriveWithImportAtTheGate();
        long trip = gate[0];
        long handover = gate[2];
        long agentLeg = gate[3];
        refuse(handover, legOnTrip(trip), " ").andExpect(status().isBadRequest());
        refuse(handover, legOnTrip(trip), "Ngựa sốt cao, không đủ sức khỏe đi tiếp").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.handovers[0].status").value("FAILED"));
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM booking_horse WHERE id = ?", String.class, line)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT pickup_missed_at IS NOT NULL FROM booking_horse WHERE id = ?", Boolean.class, line)).isTrue();
        assertThat(legStatus(legOnTrip(trip))).isEqualTo("CANCELLED");
        // Chặng đại lý đang chở kết thúc không giao: ngựa ở lại với đại lý
        assertThat(legStatus(agentLeg)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT actual_end_at IS NOT NULL FROM horse_leg WHERE id = ?", Boolean.class, agentLeg)).isTrue();
        // Không con nào lên xe: xe rời cửa khẩu không cần kiểm thanh toán, đơn không sang IN_OPERATION
        act(driver, "/trip-stops/" + gate[1] + "/depart").andExpect(status().isOk());
        assertThat(bookingStatus()).isEqualTo("CONFIRMED");
    }

    // Đơn mẫu đổi thành đơn nhập chưa trả số dư; đại lý giao, tổ xe nhận lên xe ở cửa khẩu. Trả về {lượt xe, điểm cửa khẩu}
    private long[] receiveImportAtTheGate() throws Exception {
        long[] gate = arriveWithImportAtTheGate();
        long passport = jdbc.queryForObject("""
                INSERT INTO document (document_type_id, document_requirement_id, file_url, file_name,
                                      uploaded_by, status, reviewed_by, reviewed_at, horse_id)
                SELECT dr.document_type_id, dr.id, 'https://example.test/gate-passport/' || bh.id,
                       'passport.pdf', u.id, 'APPROVED', u.id, now(), bh.horse_id
                FROM booking_horse bh CROSS JOIN (SELECT min(id) AS id FROM app_user) u
                CROSS JOIN (
                    SELECT r.id, r.document_type_id FROM document_requirement r
                    JOIN document_type t ON t.id = r.document_type_id
                    WHERE t.code = 'HORSE_PASSPORT' ORDER BY r.id LIMIT 1
                ) dr WHERE bh.id = ? RETURNING id
                """, Long.class, line);
        mvc.perform(post("/handovers/{id}/papers", gate[2]).header(HttpHeaders.AUTHORIZATION, escort)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentId\":%d,\"originalMissing\":false}".formatted(passport)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.documentId == " + passport + ")].direction").value(org.hamcrest.Matchers.contains("RECEIVED")));
        attachRecord(gate[2]);
        act(escort, "/handovers/" + gate[2] + "/complete").andExpect(status().isOk());
        return new long[] {gate[0], gate[1], passport};
    }

    // Đơn nhập đủ giấy, chưa trả số dư: xe tới cửa khẩu, mở lần nhận từ đại lý và quét chip. Trả về {lượt xe, điểm cửa khẩu, lần giao nhận, chặng đại lý}
    private long[] arriveWithImportAtTheGate() throws Exception {
        java.time.LocalDate pickup = jdbc.queryForObject("SELECT requested_pickup_date FROM booking WHERE id = ?", java.sql.Date.class, booking).toLocalDate();
        long departure = testData.departure("MOCBAI-HCM", pickup.plusDays(1), 9).getId();
        entityManager.flush();
        jdbc.update("UPDATE booking SET booking_type = 'IMPORT', departure_id = ? WHERE id = ?", departure, booking);
        jdbc.update("""
                UPDATE booking_address a SET country_code = b.country_code, province_code = b.province_code, address_line = b.address_line,
                    latitude = b.latitude, longitude = b.longitude
                FROM booking_address b WHERE a.booking_id = ? AND b.booking_id = a.booking_id AND b.id <> a.id""", booking);
        jdbc.update("DELETE FROM payment WHERE booking_id = ? AND payment_type = 'BALANCE'", booking);
        // Đơn chiếm chỗ trên chuyến mới (đổi chuyến bằng SQL nên phải ghi số chỗ đã đặt)
        jdbc.update("UPDATE departure SET booked_stall_count = 1 WHERE id = ?", departure);
        entityManager.clear();
        Instant departAt = jdbc.queryForObject("SELECT planned_departure_at FROM departure WHERE id = ?", java.sql.Timestamp.class, departure).toInstant();
        long agentLeg = id(agentLeg(agent, gate, departAt.minusSeconds(86_400), departAt).andExpect(status().isCreated()));
        act(coordinator, "/agent-legs/" + agentLeg + "/start").andExpect(status().isOk());
        long trip = linehaulTrip(departure, gate);
        List<Long> stops = stopIds(trip);
        assignLeg(trip, stops.getFirst(), stops.getLast());
        acknowledge();
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.getFirst() + "/arrive").andExpect(status().isOk());
        long handover = handoverId(handover(stops.getFirst(), agent).andExpect(status().isCreated()));
        scan(handover, legOnTrip(trip)).andExpect(status().isOk());
        // Đủ giấy: giả lập bằng đơn không cần giấy như các test khác
        entityManager.flush();
        jdbc.update("UPDATE booking SET booking_type = 'LOCAL' WHERE id = ?", booking);
        entityManager.clear();
        return new long[] {trip, stops.getFirst(), handover, agentLeg};
    }

    private ResultActions refuse(long handoverId, long legId, String reason) throws Exception {
        org.mockito.Mockito.when(cloudinary.uploadFile(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> "https://cdn.example/pickup-miss-" + System.nanoTime() + ".jpg");
        var request = new org.springframework.mock.web.MockMultipartFile("request", "", "application/json",
                "{\"horseLegId\":%d,\"reason\":\"%s\",\"reasonCode\":\"HORSE_UNWELL\"}".formatted(legId, reason)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var photo = new org.springframework.mock.web.MockMultipartFile("photo", "pickup.jpg", "image/jpeg", new byte[] {1, 2, 3});
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/handovers/{id}/refusals", handoverId)
                .file(request).file(photo).header(HttpHeaders.AUTHORIZATION, escort));
    }

    private ResultActions returnAt(long stopId) throws Exception {
        return mvc.perform(post("/trip-stops/{id}/handovers", stopId).header(HttpHeaders.AUTHORIZATION, escort)
                .contentType(MediaType.APPLICATION_JSON).content("{\"handoverType\":\"RETURN\"}"));
    }

    private void driveToTheGate(long trip, List<Long> stops) throws Exception {
        acknowledge();
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/arrive").andExpect(status().isOk());
        long hubOut = handoverId(mvc.perform(post("/trip-stops/{id}/handovers", stops.get(0)).header(HttpHeaders.AUTHORIZATION, escort)
                .contentType(MediaType.APPLICATION_JSON).content("{\"handoverType\":\"HUB_OUT\"}")).andExpect(status().isCreated()));
        scan(hubOut, legOnTrip(trip)).andExpect(status().isOk());
        act(escort, "/handovers/" + hubOut + "/complete").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(0) + "/depart").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(1) + "/arrive").andExpect(status().isOk());
        mvc.perform(post("/health-logs").header(HttpHeaders.AUTHORIZATION, escort).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingHorseId\":%d,\"tripStopId\":%d,\"healthStatus\":\"NORMAL\",\"waterIntake\":\"NORMAL\",\"feedIntake\":\"NORMAL\"}"
                        .formatted(line, stops.get(1)))).andExpect(status().isCreated());
        mvc.perform(post("/trip-stops/{id}/depart", stops.get(1)).header(HttpHeaders.AUTHORIZATION, driver).contentType(MediaType.APPLICATION_JSON)
                .content("{\"earlyReason\":\"Kịp giờ làm thủ tục cửa khẩu\"}")).andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.get(2) + "/arrive").andExpect(status().isOk());
    }

    // homeHubId: xe và tổ lái đang ở đâu khi chưa có lượt nào (lượt trục chiều về bắt đầu ở cửa khẩu)
    private long linehaulTrip(long departure, Long homeHubId) throws Exception {
        long vehicle = testData.vehicle(4, 0).getId();
        if (homeHubId != null) {
            entityManager.flush();
            jdbc.update("UPDATE vehicle SET home_hub_location_id = ? WHERE id = ?", homeHubId, vehicle);
            jdbc.update("UPDATE staff SET home_hub_location_id = ? WHERE user_id IN (?, ?)", homeHubId, driverStaff.getUserId(), escortStaff.getUserId());
            entityManager.clear();
        }
        confirmRun(departure);
        long trip = id(mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripType\":\"LINEHAUL\",\"vehicleId\":%d,\"departureId\":%d}".formatted(vehicle, departure))));
        for (Staff member : List.of(driverStaff, escortStaff)) {
            mvc.perform(post("/vehicle-trips/{id}/crew-assignments", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"staffId\":%d,\"crewRole\":\"%s\"}".formatted(member.getUserId(), member == driverStaff ? "DRIVER" : "ESCORT")))
                    .andExpect(status().isCreated());
        }
        return trip;
    }

    private void assignLeg(long trip, long board, long alight) throws Exception {
        mvc.perform(post("/vehicle-trips/{id}/horse-legs", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingHorseId\":%d,\"boardTripStopId\":%d,\"alightTripStopId\":%d}".formatted(line, board, alight)))
                .andExpect(status().isCreated());
    }

    private ResultActions agentLeg(long party, Long gateId, Instant start, Instant end) throws Exception {
        return mvc.perform(post("/booking-horses/{id}/agent-legs", line).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"agentPartyId\":%d,\"gateLocationId\":%s,\"plannedStartAt\":\"%s\",\"plannedEndAt\":\"%s\"}".formatted(party, gateId, start, end)));
    }

    private ResultActions handover(long stopId, Long partyId) throws Exception {
        return mvc.perform(post("/trip-stops/{id}/handovers", stopId).header(HttpHeaders.AUTHORIZATION, escort)
                .contentType(MediaType.APPLICATION_JSON).content("{\"handoverType\":\"AGENT\",\"partyId\":%s}".formatted(partyId)));
    }

    private void attachRecord(long handoverId) {
        jdbc.update("""
                INSERT INTO document (document_type_id, file_url, file_name, uploaded_by, status, handover_id)
                SELECT t.id, 'https://example.test/record.pdf', 'record.pdf', h.handled_by, 'UPLOADED', h.id
                FROM document_type t CROSS JOIN handover h WHERE t.code = 'HANDOVER_RECORD' AND h.id = ?
                """, handoverId);
    }

    private ResultActions scan(long handoverId, long legId) throws Exception {
        return mvc.perform(post("/handovers/{id}/horses", handoverId).header(HttpHeaders.AUTHORIZATION, escort)
                .contentType(MediaType.APPLICATION_JSON).content("{\"horseLegId\":%d,\"conditionNote\":\"Khỏe\",\"microchip\":\"%s\"}".formatted(legId, chip)));
    }

    private long legOnTrip(long trip) {
        return jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND vehicle_trip_id = ?", Long.class, line, trip);
    }

    private String bookingStatus() {
        entityManager.flush();
        return jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, booking);
    }

    private String legStatus(long legId) {
        return jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, legId);
    }

    private void acknowledge() throws Exception {
        long assignment = ((Number) JsonPath.read(content(get("/crew-assignments/mine"), driver), "$.data[0].id")).longValue();
        act(driver, "/crew-assignments/" + assignment + "/acknowledge").andExpect(status().isOk());
    }

    private List<Long> stopIds(long trip) throws Exception {
        return ((List<Number>) JsonPath.read(content(get("/vehicle-trips/" + trip), coordinator), "$.data.stops[*].id")).stream().map(Number::longValue).toList();
    }

    private ResultActions act(String token, String path) throws Exception {
        if (path.matches("/agent-legs/[0-9]+/complete")) {
            when(cloudinary.uploadFile(any(), anyString())).thenReturn("https://example.test/proof.pdf");
            return mvc.perform(multipart(path).file(new MockMultipartFile("proof", "proof.pdf", "application/pdf", new byte[]{1}))
                    .header(HttpHeaders.AUTHORIZATION, token));
        }
        return mvc.perform(HandoverEvidenceFixtures.complete(mvc, jdbc, jwtUtils, path).header(HttpHeaders.AUTHORIZATION, token));
    }

    private String content(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static long handoverId(ResultActions result) throws Exception {
        List<Number> ids = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.handovers[*].id");
        return ids.getLast().longValue();
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private String token(Staff staff, String role) {
        return "Bearer " + jwtUtils.generateToken(staff.getUser().getUsername(), List.of(role));
    }

    // Quản lý đã xác nhận chạy chuyến (A6b): điều kiện để lập lượt trục
    private void confirmRun(long departureId) {
        entityManager.flush();
        jdbc.update("UPDATE departure SET run_confirmed_at = now(), run_confirmed_by = (SELECT min(id) FROM app_user) WHERE id = ? AND run_confirmed_at IS NULL", departureId);
        entityManager.clear();
    }

    // Thông báo khách nhận được trên đơn (B6), theo thứ tự gửi
    private List<String> customerNotices(long bookingId) {
        entityManager.flush();
        return jdbc.queryForList("SELECT n.title FROM notification n JOIN notification_recipient r ON r.notification_id = n.id "
                + "JOIN booking b ON b.id = n.booking_id WHERE n.booking_id = ? AND r.user_id = b.customer_id ORDER BY n.id", String.class, bookingId);
    }
}
