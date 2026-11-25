package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
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

// B1 (màn 3.9a, quy tắc #39, #40): hệ thống tự xếp khoang khi gán ngựa, dồn chỗ khi lẻ vị trí; điều phối lưu sơ đồ,
// phát hiện người khác vừa đổi; chưa xếp đủ thì không xuất phát, xe xuất phát thì khóa sơ đồ. Đơn mẫu DEV-DOMESTIC-001 trên xe 3 chỗ, vách di động
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class StallPlanFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private EntityManager entityManager;

    private String coordinator;
    private String driver;
    private Staff driverStaff;
    private long trip;
    private List<Long> stops;
    private List<Long> lines;

    @BeforeEach
    void setUp() throws Exception {
        coordinator = token(testData.staff(testData.unique("stall.coordinator"), "COORDINATOR"), "COORDINATOR");
        driverStaff = testData.staff(testData.unique("stall.driver"), "DRIVER");
        driver = token(driverStaff, "DRIVER");
        long booking = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DOMESTIC-001'", Long.class);
        long departure = jdbc.queryForObject("SELECT departure_id FROM booking WHERE id = ?", Long.class, booking);
        lines = jdbc.queryForList("SELECT id FROM booking_horse WHERE booking_id = ? ORDER BY id", Long.class, booking).subList(0, 2);
        // Con thứ hai đi khoang rộng: chiếm hai vị trí liền nhau
        entityManager.flush();
        jdbc.update("UPDATE booking_horse SET stall_type_code = 'WIDE' WHERE id = ?", lines.get(1));
        entityManager.clear();
        confirmRun(departure);
        trip = ((Number) JsonPath.read(mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripType\":\"LINEHAUL\",\"vehicleId\":%d,\"departureId\":%d}".formatted(testData.vehicle(3, 0).getId(), departure)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
        stops = ((List<Number>) JsonPath.read(content(get("/vehicle-trips/" + trip)), "$.data.stops[*].id")).stream().map(Number::longValue).toList();
    }

    @Test
    void systemPlacesEachHorseAndCompactsWhenTheWideStallDoesNotFit() throws Exception {
        assign(lines.get(0)).andExpect(status().isCreated()).andExpect(jsonPath("$.data[0].leg.stallPosition").value(1));
        // Điều phối dời con thường vào giữa xe: vị trí 1 và 3 trống nhưng không liền nhau
        String plan = content(get("/vehicle-trips/" + trip + "/stall-plan"));
        long first = ((Number) JsonPath.read(plan, "$.data.horses[0].horseLegId")).longValue();
        apply(List.of(new long[] {first, 1, 2})).andExpect(status().isOk()).andExpect(jsonPath("$.data.horses[0].stallPosition").value(2));

        // Gán con khoang rộng: hệ thống dồn con chưa lên xe để có hai vị trí liền nhau
        assign(lines.get(1)).andExpect(status().isCreated());
        assertThat(position(lines.get(0))).isEqualTo(1);
        assertThat(position(lines.get(1))).isEqualTo(2);
        mvc.perform(get("/vehicle-trips/{id}/stall-plan", trip).header(HttpHeaders.AUTHORIZATION, driver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capacity").value(3)).andExpect(jsonPath("$.data.editable").value(true))
                .andExpect(jsonPath("$.data.horses[1].seats").value(2));
    }

    @Test
    void savingDetectsAChangedPlanAndRejectsOverlaps() throws Exception {
        assign(lines.get(0)).andExpect(status().isCreated());
        assign(lines.get(1)).andExpect(status().isCreated());
        long standard = legOf(lines.get(0));
        long wide = legOf(lines.get(1));
        // Sơ đồ đang nhìn đã cũ (vị trí hiện tại khác) hoặc thiếu con: 409
        apply(List.of(new long[] {standard, 3, 3}, new long[] {wide, 2, 1})).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Sơ đồ khoang vừa thay đổi")));
        apply(List.of(new long[] {standard, 1, 3})).andExpect(status().isConflict());
        // Khoang rộng ở vị trí 2 chiếm 2 và 3: trùng con thường ở 3
        apply(List.of(new long[] {standard, 1, 3}, new long[] {wide, 2, 2})).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Vị trí")));
        // Đổi chỗ hợp lệ: khoang rộng lên đầu xe, con thường ra cửa
        apply(List.of(new long[] {standard, 1, 3}, new long[] {wide, 2, 1})).andExpect(status().isOk());
        assertThat(position(lines.get(0))).isEqualTo(3);
        assertThat(position(lines.get(1))).isEqualTo(1);
        // Xếp lại tự động chỉ trả về, không lưu
        mvc.perform(post("/vehicle-trips/{id}/stall-plan/suggest", trip).header(HttpHeaders.AUTHORIZATION, coordinator)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.horses.length()").value(2));
        assertThat(position(lines.get(0))).isEqualTo(3);
        // Tổ xe chỉ xem
        mvc.perform(post("/vehicle-trips/{id}/stall-plan/suggest", trip).header(HttpHeaders.AUTHORIZATION, driver)).andExpect(status().isForbidden());
    }

    @Test
    void planIsLockedOnceTheVehicleDeparts() throws Exception {
        assign(lines.get(0)).andExpect(status().isCreated());
        long standard = legOf(lines.get(0));
        Staff escortStaff = testData.staff(testData.unique("stall.escort"), "ESCORT");
        for (Staff member : List.of(driverStaff, escortStaff)) {
            String role = member == driverStaff ? "DRIVER" : "ESCORT";
            mvc.perform(post("/vehicle-trips/{id}/crew-assignments", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"staffId\":%d,\"crewRole\":\"%s\"}".formatted(member.getUserId(), role))).andExpect(status().isCreated());
            String crewToken = token(member, role);
            long assignment = ((Number) JsonPath.read(mvc.perform(get("/crew-assignments/mine").header(HttpHeaders.AUTHORIZATION, crewToken))
                    .andReturn().getResponse().getContentAsString(), "$.data[0].id")).longValue();
            mvc.perform(post("/crew-assignments/{id}/acknowledge", assignment).header(HttpHeaders.AUTHORIZATION, crewToken)).andExpect(status().isOk());
        }
        // Quy tắc #40: còn con chưa có vị trí (chặng lập trước khi có sơ đồ) thì chưa xuất phát
        entityManager.flush();
        jdbc.update("UPDATE horse_leg SET stall_position = NULL WHERE id = ?", standard);
        entityManager.clear();
        mvc.perform(post("/vehicle-trips/{id}/start", trip).header(HttpHeaders.AUTHORIZATION, driver)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Còn ngựa chưa có vị trí khoang")));
        mvc.perform(post("/vehicle-trips/{id}/stall-plan/apply", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"horses\":[{\"horseLegId\":%d,\"stallPosition\":1}]}".formatted(standard))).andExpect(status().isOk());
        mvc.perform(post("/vehicle-trips/{id}/start", trip).header(HttpHeaders.AUTHORIZATION, driver)).andExpect(status().isOk());

        apply(List.of(new long[] {standard, 1, 2})).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Lượt xe đã xuất phát hoặc đã kết thúc, sơ đồ khoang đã khóa."));
        mvc.perform(get("/vehicle-trips/{id}/stall-plan", trip).header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(jsonPath("$.data.editable").value(false)).andExpect(jsonPath("$.data.horses[0].stallPosition").value(1));
    }

    private ResultActions assign(long line) throws Exception {
        return mvc.perform(post("/vehicle-trips/{id}/horse-legs", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingHorseId\":%d,\"boardTripStopId\":%d,\"alightTripStopId\":%d}".formatted(line, stops.getFirst(), stops.getLast())));
    }

    // Mỗi phần tử: {horseLegId, vị trí đang nhìn, vị trí mới}
    private ResultActions apply(List<long[]> rows) throws Exception {
        String body = rows.stream().map(r -> "{\"horseLegId\":%d,\"currentPosition\":%d,\"stallPosition\":%d}".formatted(r[0], r[1], r[2]))
                .reduce((a, b) -> a + "," + b).orElse("");
        return mvc.perform(post("/vehicle-trips/{id}/stall-plan/apply", trip).header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"horses\":[" + body + "]}"));
    }

    private long legOf(long line) {
        return jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND vehicle_trip_id = ?", Long.class, line, trip);
    }

    private Integer position(long line) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT stall_position FROM horse_leg WHERE booking_horse_id = ? AND vehicle_trip_id = ?", Integer.class, line, trip);
    }

    private String content(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, coordinator)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
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
