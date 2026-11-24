package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
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
import java.util.ArrayList;
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

// B2 (MAN_HINH 3.5c): khách tự chở ngựa tới hub và người nhận tự tới hub nhận (SELF). Đơn mẫu DEV-DOMESTIC-001 đi trục HCM – Đà Nẵng
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class HubSelfFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private LocationRepository locations;
    @Autowired private EntityManager entityManager;

    private String coordinator;
    private Staff driverStaff;
    private Staff escortStaff;
    private String driver;
    private String escort;
    private long booking;
    private long departure;
    private Instant departAt;
    private List<Long> lines;

    @BeforeEach
    void setUp() {
        coordinator = token(testData.staff(testData.unique("hub.coordinator"), "COORDINATOR"), "COORDINATOR");
        driverStaff = testData.staff(testData.unique("hub.driver"), "DRIVER");
        escortStaff = testData.staff(testData.unique("hub.escort"), "ESCORT");
        driver = token(driverStaff, "DRIVER");
        escort = token(escortStaff, "ESCORT");
        booking = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DOMESTIC-001'", Long.class);
        departure = jdbc.queryForObject("SELECT departure_id FROM booking WHERE id = ?", Long.class, booking);
        departAt = jdbc.queryForObject("SELECT planned_departure_at FROM departure WHERE id = ?", java.sql.Timestamp.class, departure).toInstant();
        lines = jdbc.queryForList("SELECT id FROM booking_horse WHERE booking_id = ? ORDER BY id", Long.class, booking);
    }

    @Test
    void selfDroppedHorsesAreReceivedAtTheHubAndTheVehicleLeavesOnlyWhenPaid() throws Exception {
        sql("UPDATE booking SET pickup_method = 'SELF' WHERE id = " + booking);
        sql("DELETE FROM payment WHERE booking_id = " + booking + " AND payment_type = 'BALANCE'");
        long direct = jdbc.queryForObject("SELECT min(bh.id) FROM booking_horse bh JOIN booking b ON b.id = bh.booking_id WHERE b.code = 'DEV-DIRECT-001'", Long.class);
        appoint(direct, departAt.minusSeconds(4 * 3600)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chỉ hẹn nhận tại hub cho đơn đi trục mà khách tự chở ngựa tới hub."));

        List<Long> stays = new ArrayList<>();
        for (long line : lines) {
            stays.add(id(appoint(line, departAt.minusSeconds(4 * 3600)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.pickupMethod").value("SELF"))));
        }
        appoint(lines.getFirst(), departAt.minusSeconds(3 * 3600)).andExpect(status().isConflict());
        long hub = locations.findByCode("HUB-HCM").orElseThrow().getId();
        mvc.perform(get("/horse-stays/appointments").param("locationId", String.valueOf(hub)).header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(lines.size()));
        // Đơn rời chuyến (chờ đổi chuyến) thì lịch hẹn không còn hiện
        sql("UPDATE booking SET status = 'SCHEDULE_CHANGE_REQUIRED' WHERE id = " + booking);
        mvc.perform(get("/horse-stays/appointments").param("locationId", String.valueOf(hub)).header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        sql("UPDATE booking SET status = 'CONFIRMED' WHERE id = " + booking);

        // Chưa xếp lượt trục thì chưa nhận: đầu SELF không có chặng đón, ngựa ghi vào chặng trục
        receive(stays.getFirst(), chip(lines.getFirst())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Con ngựa chưa được xếp lượt xe trục")));
        long trip = linehaulTrip();
        List<Long> stops = stopIds(trip);
        for (long line : lines) assign(trip, line, stops.getFirst(), stops.getLast());

        // Kiểm giấy khi nhận ở hub (đơn liên tỉnh mẫu còn thiếu giấy)
        receive(stays.getFirst(), chip(lines.getFirst())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Đơn DEV-DOMESTIC-001 còn thiếu giấy tờ bắt buộc")));
        sql("UPDATE booking SET booking_type = 'LOCAL' WHERE id = " + booking);
        receive(stays.getFirst(), "985000000999999").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chip không khớp hồ sơ ngựa. Không giao nhận."));
        for (int i = 0; i < lines.size(); i++) {
            receive(stays.get(i), chip(lines.get(i))).andExpect(status().isOk()).andExpect(jsonPath("$.data.actualStartAt").isNotEmpty());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM handover WHERE handover_type = 'PICKUP' AND status = 'DONE' AND horse_stay_id IN ("
                + join(stays) + ")", Long.class)).isEqualTo(lines.size());
        // Nhận xong thì lập khoản số dư
        assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE booking_id = ? AND payment_type = 'BALANCE'", String.class, booking)).isEqualTo("PENDING");

        acknowledge();
        act(driver, "/vehicle-trips/" + trip + "/start").andExpect(status().isOk());
        act(driver, "/trip-stops/" + stops.getFirst() + "/arrive").andExpect(status().isOk());
        long hubOut = handoverId(mvc.perform(post("/trip-stops/{id}/handovers", stops.getFirst()).header(HttpHeaders.AUTHORIZATION, escort)
                .contentType(MediaType.APPLICATION_JSON).content("{\"handoverType\":\"HUB_OUT\"}")).andExpect(status().isCreated()));
        for (long line : lines) {
            mvc.perform(post("/handovers/{id}/horses", hubOut).header(HttpHeaders.AUTHORIZATION, escort).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"horseLegId\":%d,\"microchip\":\"%s\"}".formatted(leg(trip, line), chip(line)))).andExpect(status().isOk());
        }
        act(escort, "/handovers/" + hubOut + "/complete").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horse_stay WHERE id IN (" + join(stays) + ") AND actual_end_at IS NOT NULL", Long.class))
                .isEqualTo(lines.size());

        // Hub là nơi nhận của đầu SELF: chưa trả đủ thì xe không rời hub (#32)
        act(driver, "/trip-stops/" + stops.getFirst() + "/depart").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Đơn DEV-DOMESTIC-001 chưa trả đủ")));
        sql("UPDATE payment SET status = 'PAID', paid_at = now() WHERE booking_id = " + booking + " AND payment_type = 'BALANCE'");
        act(driver, "/trip-stops/" + stops.getFirst() + "/depart").andExpect(status().isOk());
        assertThat(bookingStatus()).isEqualTo("IN_OPERATION");
    }

    @Test
    void consigneeCollectsTheHorsesAtTheHubAndTheBookingIsDelivered() throws Exception {
        long trip = linehaulTrip();
        List<Long> stops = stopIds(trip);
        for (long line : lines) assign(trip, line, stops.getFirst(), stops.getLast());
        // Dựng sẵn: chuyến trục đã tới hub Đà Nẵng, ngựa đã giao vào hub và đang tập kết
        long hubDn = locations.findByCode("HUB-DN").orElseThrow().getId();
        sql("UPDATE booking SET status = 'IN_OPERATION' WHERE id = " + booking);
        sql("UPDATE horse_leg SET status = 'COMPLETED', actual_start_at = now() - interval '1 day', actual_end_at = now() - interval '1 hour' WHERE vehicle_trip_id = " + trip);
        List<Long> stays = new ArrayList<>();
        for (long line : lines) {
            stays.add(jdbc.queryForObject("""
                    INSERT INTO horse_stay (booking_horse_id, stay_type, location_id, planned_start_at, planned_end_at, actual_start_at)
                    VALUES (?, 'ASSEMBLY', ?, now() - interval '1 hour', now() + interval '1 day', now() - interval '1 hour') RETURNING id""",
                    Long.class, line, hubDn));
        }

        // Đơn giao tận nơi thì không trả ở hub
        deliver(stays.getFirst(), chip(lines.getFirst())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Con ngựa này không chờ người nhận tới hub."));
        sql("UPDATE booking SET delivery_method = 'SELF' WHERE id = " + booking);
        deliver(stays.getFirst(), "985000000999999").andExpect(status().isConflict());
        deliver(stays.getFirst(), chip(lines.getFirst())).andExpect(status().isOk()).andExpect(jsonPath("$.data.actualEndAt").isNotEmpty());
        assertThat(bookingStatus()).isEqualTo("IN_OPERATION");
        for (int i = 1; i < lines.size(); i++) {
            deliver(stays.get(i), chip(lines.get(i))).andExpect(status().isOk());
        }
        // Quy tắc #16: con cuối giao xong (lần giao DELIVERY tại hub) thì đơn DELIVERED
        assertThat(bookingStatus()).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM handover WHERE handover_type = 'DELIVERY' AND horse_stay_id IN (" + join(stays) + ")", Long.class))
                .isEqualTo(lines.size());
    }

    @Test
    void lateConsigneeHorseGoesToStablingAndTheConsigneePaysTheFee() throws Exception {
        long trip = linehaulTrip();
        List<Long> stops = stopIds(trip);
        for (long line : lines) assign(trip, line, stops.getFirst(), stops.getLast());
        long hubDn = locations.findByCode("HUB-DN").orElseThrow().getId();
        sql("UPDATE booking SET status = 'IN_OPERATION', delivery_method = 'SELF' WHERE id = " + booking);
        sql("UPDATE horse_leg SET status = 'COMPLETED', actual_start_at = now() - interval '1 day', actual_end_at = now() - interval '2 hour' WHERE vehicle_trip_id = " + trip);
        List<Long> stays = new ArrayList<>();
        for (long line : lines) {
            stays.add(jdbc.queryForObject("""
                    INSERT INTO horse_stay (booking_horse_id, stay_type, location_id, planned_start_at, planned_end_at, actual_start_at)
                    VALUES (?, 'ASSEMBLY', ?, now() - interval '2 hour', now() + interval '1 day', now() - interval '2 hour') RETURNING id""",
                    Long.class, line, hubDn));
        }
        long first = stays.getFirst();

        // Chưa quá giờ hẹn thì chưa gửi chuồng
        stable(first, "500000").andExpect(status().isConflict()).andExpect(jsonPath("$.message", startsWith("Chưa quá giờ hẹn người nhận tới")));
        // Điều phối hẹn người nhận tới 30 phút trước; giờ hẹn không được trước lúc ngựa tới hub
        collectAt(first, java.time.Instant.now().minusSeconds(3 * 3600)).andExpect(status().isBadRequest());
        collectAt(first, java.time.Instant.now().minusSeconds(1800)).andExpect(status().isOk());
        long stabling = ((Number) JsonPath.read(content(stable(first, "500000").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stayType").value("STABLING"))), "$.data.id")).longValue();
        assertThat(jdbc.queryForObject("SELECT actual_end_at IS NOT NULL FROM horse_stay WHERE id = ?", Boolean.class, first)).isTrue();
        var fee = jdbc.queryForMap("SELECT category, payer_type, amount, horse_stay_id, approved_at FROM cost_item WHERE horse_stay_id = ?", stabling);
        assertThat(fee.get("category")).isEqualTo("STABLING");
        assertThat(fee.get("payer_type")).isEqualTo("CONSIGNEE");
        assertThat((java.math.BigDecimal) fee.get("amount")).isEqualByComparingTo("500000");
        assertThat(fee.get("approved_at")).isNull();

        // Người nhận tới: trả ngựa từ chuồng gửi, đơn giao xong khi con cuối được nhận
        deliver(stabling, chip(lines.getFirst())).andExpect(status().isOk()).andExpect(jsonPath("$.data.actualEndAt").isNotEmpty());
        for (int i = 1; i < lines.size(); i++) deliver(stays.get(i), chip(lines.get(i))).andExpect(status().isOk());
        assertThat(bookingStatus()).isEqualTo("DELIVERED");
    }

    private ResultActions stable(long stay, String fee) throws Exception {
        return mvc.perform(post("/horse-stays/{id}/stabling", stay).header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"fee\":%s,\"note\":\"Người nhận báo tới muộn\"}".formatted(fee)));
    }

    private ResultActions collectAt(long stay, java.time.Instant at) throws Exception {
        return mvc.perform(post("/horse-stays/{id}/collection-time", stay).header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"collectAt\":\"%s\"}".formatted(at)));
    }

    private static String content(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private long linehaulTrip() throws Exception {
        confirmRun(departure);
        long trip = id(mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripType\":\"LINEHAUL\",\"vehicleId\":%d,\"departureId\":%d}".formatted(testData.vehicle(4, 0).getId(), departure))));
        for (Staff member : List.of(driverStaff, escortStaff)) {
            mvc.perform(post("/vehicle-trips/{id}/crew-assignments", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"staffId\":%d,\"crewRole\":\"%s\"}".formatted(member.getUserId(), member == driverStaff ? "DRIVER" : "ESCORT")))
                    .andExpect(status().isCreated());
        }
        return trip;
    }

    private void assign(long trip, long line, long board, long alight) throws Exception {
        mvc.perform(post("/vehicle-trips/{id}/horse-legs", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingHorseId\":%d,\"boardTripStopId\":%d,\"alightTripStopId\":%d}".formatted(line, board, alight)))
                .andExpect(status().isCreated());
    }

    private ResultActions appoint(long line, Instant at) throws Exception {
        return mvc.perform(post("/booking-horses/{id}/hub-appointments", line).header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"plannedStartAt\":\"%s\"}".formatted(at)));
    }

    private ResultActions receive(long stay, String microchip) throws Exception {
        return mvc.perform(post("/horse-stays/{id}/receipts", stay).header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"microchip\":\"%s\"}".formatted(microchip)));
    }

    private ResultActions deliver(long stay, String microchip) throws Exception {
        return mvc.perform(post("/horse-stays/{id}/deliveries", stay).header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"microchip\":\"%s\",\"conditionNote\":\"Khỏe\"}".formatted(microchip)));
    }

    // Sửa thẳng DB rồi bỏ bộ đệm của JPA để service đọc lại
    private void sql(String statement) {
        entityManager.flush();
        jdbc.update(statement);
        entityManager.clear();
    }

    private String chip(long line) {
        return jdbc.queryForObject("SELECT h.microchip FROM booking_horse bh JOIN horse h ON h.id = bh.horse_id WHERE bh.id = ?", String.class, line);
    }

    private long leg(long trip, long line) {
        return jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND vehicle_trip_id = ?", Long.class, line, trip);
    }

    private String bookingStatus() {
        entityManager.flush();
        return jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, booking);
    }

    private static String join(List<Long> ids) {
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }

    private void acknowledge() throws Exception {
        long assignment = ((Number) JsonPath.read(content(get("/crew-assignments/mine"), driver), "$.data[0].id")).longValue();
        act(driver, "/crew-assignments/" + assignment + "/acknowledge").andExpect(status().isOk());
    }

    private List<Long> stopIds(long trip) throws Exception {
        return ((List<Number>) JsonPath.read(content(get("/vehicle-trips/" + trip), coordinator), "$.data.stops[*].id")).stream().map(Number::longValue).toList();
    }

    private ResultActions act(String token, String path) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, token));
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
}
