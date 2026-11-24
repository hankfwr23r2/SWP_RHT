package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
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

// B4: thực hiện phương án đã duyệt. Đổi xe: lượt đang chạy chuyển sang xe thay thế. Về hub: ngựa dừng chặng giao, giao vào hub thì tập kết không phí
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class IncidentActionFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private LocationRepository locations;
    @Autowired private EntityManager entityManager;

    private String coordinator;
    private String manager;
    private String escort;
    private long trip;
    private long leg;
    private long line;
    private List<Number> stops;

    @BeforeEach
    void setUp() throws Exception {
        coordinator = token(testData.staff(testData.unique("action.coordinator"), "COORDINATOR"), "COORDINATOR");
        manager = token(testData.staff(testData.unique("action.manager"), "MANAGER"), "MANAGER");
        Staff escortStaff = testData.staff(testData.unique("action.escort"), "ESCORT");
        escort = token(escortStaff, "ESCORT");
        long booking = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DIRECT-001'", Long.class);
        long pickup = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'PICKUP'", Long.class, booking);
        long delivery = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'DELIVERY'", Long.class, booking);
        line = jdbc.queryForObject("SELECT id FROM booking_horse WHERE booking_id = ?", Long.class, booking);
        long hcm = locations.findByCode("HUB-HCM").orElseThrow().getId();
        Instant start = Instant.now().minusSeconds(3600).atZone(ZoneOffset.UTC).toInstant();
        String created = mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripType\":\"DIRECT\",\"vehicleId\":%d,\"stops\":[%s,%s,%s,%s]}".formatted(testData.vehicle(2, 0).getId(),
                        stop("locationId", hcm, "HUB", start, 0), stop("bookingAddressId", pickup, "PICKUP", start, 60),
                        stop("bookingAddressId", delivery, "DELIVERY", start, 240), stop("locationId", hcm, "HUB", start, 480))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        trip = ((Number) JsonPath.read(created, "$.data.id")).longValue();
        stops = JsonPath.read(created, "$.data.stops[*].id");
        mvc.perform(post("/vehicle-trips/{id}/crew-assignments", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":%d,\"crewRole\":\"ESCORT\"}".formatted(escortStaff.getUserId()))).andExpect(status().isCreated());
        mvc.perform(post("/vehicle-trips/{id}/horse-legs", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingHorseId\":%d,\"boardTripStopId\":%d,\"alightTripStopId\":%d}".formatted(line, stops.get(1).longValue(), stops.get(2).longValue())))
                .andExpect(status().isCreated());
        leg = jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND vehicle_trip_id = ?", Long.class, line, trip);
        // Xe đang chạy: đã nhận ngựa ở điểm đón, đang trên đường tới điểm giao
        entityManager.flush();
        jdbc.update("UPDATE vehicle_trip SET status = 'IN_PROGRESS', actual_start_at = now() WHERE id = ?", trip);
        jdbc.update("UPDATE trip_stop SET actual_arrival_at = now(), actual_departure_at = now() WHERE id IN (?, ?)", stops.get(0).longValue(), stops.get(1).longValue());
        jdbc.update("UPDATE horse_leg SET status = 'IN_PROGRESS', actual_start_at = now() WHERE id = ?", leg);
        entityManager.clear();
    }

    @Test
    void approvedVehicleTransferMovesTheRunningTripToTheReplacementVehicle() throws Exception {
        long incident = id(report("VEHICLE_BREAKDOWN", "COMPANY_FAULT"));
        // Con đang ở chỗ số 2 của xe 2 chỗ: xe thay thế 1 chỗ không đủ
        long tooSmall = testData.vehicle(1, 0).getId();
        jdbc.update("UPDATE horse_leg SET stall_position = 2 WHERE id = ?", leg);
        long rescue = testData.vehicle(2, 0).getId();
        swap(incident, rescue).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Sự cố chưa có phương án TRANSFER_VEHICLE đã được quản lý duyệt."));
        approve(incident, "TRANSFER_VEHICLE", party("Đội cứu hộ xe tải (mẫu)"));
        swap(incident, tooSmall).andExpect(status().isConflict()).andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("chỉ có 1 chỗ")));
        long current = jdbc.queryForObject("SELECT vehicle_id FROM vehicle_trip WHERE id = ?", Long.class, trip);
        swap(incident, current).andExpect(status().isBadRequest());
        swap(incident, rescue).andExpect(status().isOk());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT vehicle_id FROM vehicle_trip WHERE id = ?", Long.class, trip)).isEqualTo(rescue);
        // Ngựa vẫn trên lượt cũ, chặng không đổi
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, leg)).isEqualTo("IN_PROGRESS");
        mvc.perform(post("/incidents/{id}/vehicle-swap", incident).header(HttpHeaders.AUTHORIZATION, escort).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleId\":%d}".formatted(rescue))).andExpect(status().isForbidden());
    }

    @Test
    void approvedReturnToHubStopsTheDeliveryAndTheHorseWaitsAtTheHubWithoutFee() throws Exception {
        long incident = id(report("ACCIDENT", "COMPANY_FAULT"));
        approve(incident, "RETURN_TO_HUB", party("Đội cứu hộ xe tải (mẫu)"));
        act(coordinator, "/incidents/" + incident + "/return-to-hub").andExpect(status().isOk());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, leg)).isEqualTo("FAILED");
        act(coordinator, "/incidents/" + incident + "/return-to-hub").andExpect(status().isConflict());

        // Bỏ qua điểm giao, tới hub: giao con vào hub thì tập kết chờ giao lại, không ghi phí
        jdbc.update("UPDATE trip_stop SET actual_arrival_at = now(), actual_departure_at = now() WHERE id = ?", stops.get(2).longValue());
        jdbc.update("UPDATE trip_stop SET actual_arrival_at = now() WHERE id = ?", stops.get(3).longValue());
        entityManager.clear();
        String opened = mvc.perform(post("/trip-stops/{id}/handovers", stops.get(3).longValue()).header(HttpHeaders.AUTHORIZATION, escort)
                .contentType(MediaType.APPLICATION_JSON).content("{\"handoverType\":\"HUB_IN\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        List<Number> handovers = JsonPath.read(opened, "$.data.handovers[*].id");
        long hubIn = handovers.getLast().longValue();
        String chip = jdbc.queryForObject("SELECT h.microchip FROM booking_horse bh JOIN horse h ON h.id = bh.horse_id WHERE bh.id = ?", String.class, line);
        mvc.perform(post("/handovers/{id}/horses", hubIn).header(HttpHeaders.AUTHORIZATION, escort).contentType(MediaType.APPLICATION_JSON)
                .content("{\"horseLegId\":%d,\"microchip\":\"%s\"}".formatted(leg, chip))).andExpect(status().isOk());
        act(escort, "/handovers/" + hubIn + "/complete").andExpect(status().isOk());
        entityManager.flush();
        long stay = jdbc.queryForObject("SELECT id FROM horse_stay WHERE booking_horse_id = ? AND actual_end_at IS NULL", Long.class, line);
        assertThat(jdbc.queryForObject("SELECT stay_type FROM horse_stay WHERE id = ?", String.class, stay)).isEqualTo("ASSEMBLY");
        act(escort, "/vehicle-trips/" + trip + "/complete").andExpect(status().isOk());
    }

    private void approve(long incident, String type, long handler) throws Exception {
        String body = mvc.perform(post("/incidents/{id}/plans", incident).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolutionType\":\"%s\",\"instructions\":\"Chỉ đạo\",\"estimatedCost\":1000000,\"handlerPartyId\":%d}".formatted(type, handler)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long plan = ((Number) JsonPath.read(body, "$.data.plans[0].id")).longValue();
        mvc.perform(post("/incident-plans/{id}/approve", plan).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isOk());
    }

    private ResultActions report(String type, String cause) throws Exception {
        return mvc.perform(post("/incidents").header(HttpHeaders.AUTHORIZATION, escort).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleTripId\":%d,\"incidentType\":\"%s\",\"causeType\":\"%s\",\"title\":\"Sự cố\",\"description\":\"Mô tả\"}".formatted(trip, type, cause)))
                .andExpect(status().isCreated());
    }

    private ResultActions swap(long incident, long vehicle) throws Exception {
        return mvc.perform(post("/incidents/{id}/vehicle-swap", incident).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleId\":%d}".formatted(vehicle)));
    }

    private ResultActions act(String token, String path) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, token));
    }

    private long party(String name) {
        return jdbc.queryForObject("SELECT id FROM party WHERE display_name = ?", Long.class, name);
    }

    private static long id(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private static String stop(String key, long id, String type, Instant base, int minutes) {
        String at = base.plusSeconds(60L * minutes).toString();
        String leave = base.plusSeconds(60L * (minutes + 30)).toString();
        return "{\"%s\":%d,\"stopType\":\"%s\",\"plannedArrivalAt\":\"%s\",\"plannedDepartureAt\":\"%s\"}".formatted(key, id, type, at, leave);
    }

    private String token(Staff staff, String role) {
        return "Bearer " + jwtUtils.generateToken(staff.getUser().getUsername(), List.of(role));
    }
}
