package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.CostItemService;
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

// B4: báo sự cố, phương án có phiên bản, duyệt, xác nhận đủ sức khỏe, đóng; chi phí theo nguyên nhân và hạn mức khẩn cấp
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class IncidentFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private LocationRepository locations;
    @Autowired private EntityManager entityManager;
    @Autowired private CostItemService costService;

    private String coordinator;
    private String manager;
    private long coordinatorId;
    private String escort;
    private long trip;
    private long leg;
    private long booking;

    @BeforeEach
    void setUp() throws Exception {
        Staff coordinatorStaff = testData.staff(testData.unique("incident.coordinator"), "COORDINATOR");
        coordinatorId = coordinatorStaff.getUserId();
        coordinator = token(coordinatorStaff, "COORDINATOR");
        manager = token(testData.staff(testData.unique("incident.manager"), "MANAGER"), "MANAGER");
        Staff escortStaff = testData.staff(testData.unique("incident.escort"), "ESCORT");
        escort = token(escortStaff, "ESCORT");
        booking = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DIRECT-001'", Long.class);
        long pickup = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'PICKUP'", Long.class, booking);
        long delivery = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'DELIVERY'", Long.class, booking);
        long line = jdbc.queryForObject("SELECT id FROM booking_horse WHERE booking_id = ?", Long.class, booking);
        long hcm = locations.findByCode("HUB-HCM").orElseThrow().getId();
        Instant start = jdbc.queryForObject("SELECT requested_pickup_date FROM booking WHERE id = ?", java.sql.Date.class, booking)
                .toLocalDate().atTime(1, 0).toInstant(ZoneOffset.UTC);
        String created = mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripType\":\"DIRECT\",\"vehicleId\":%d,\"stops\":[%s,%s,%s,%s]}".formatted(testData.vehicle(2, 0).getId(),
                        stop("locationId", hcm, "HUB", start, 0), stop("bookingAddressId", pickup, "PICKUP", start, 60),
                        stop("bookingAddressId", delivery, "DELIVERY", start, 240), stop("locationId", hcm, "HUB", start, 480))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        trip = ((Number) JsonPath.read(created, "$.data.id")).longValue();
        List<Number> stops = JsonPath.read(created, "$.data.stops[*].id");
        mvc.perform(post("/vehicle-trips/{id}/crew-assignments", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":%d,\"crewRole\":\"ESCORT\"}".formatted(escortStaff.getUserId()))).andExpect(status().isCreated());
        mvc.perform(post("/vehicle-trips/{id}/horse-legs", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingHorseId\":%d,\"boardTripStopId\":%d,\"alightTripStopId\":%d}".formatted(line, stops.get(1).longValue(), stops.get(2).longValue())))
                .andExpect(status().isCreated());
        leg = jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND vehicle_trip_id = ?", Long.class, line, trip);
        jdbc.update("UPDATE vehicle_trip SET status = 'IN_PROGRESS', actual_start_at = now() WHERE id = ?", trip);
        entityManager.clear();
    }

    @Test
    void vehicleIncidentGoesThroughVersionedPlansToClosed() throws Exception {
        long incident = id(report(null, "VEHICLE_BREAKDOWN", "COMPANY_FAULT").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("REPORTED")));
        report(null, "DELAY", "FORCE_MAJEURE").andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Lượt xe đang có sự cố chưa đóng."));

        propose(incident, "VET_TREATMENT", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Phương án VET_TREATMENT cần chọn bên xử lý."));
        propose(incident, "STABLE_NEARBY", party("Phòng khám thú y ngựa (mẫu)")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bên xử lý không có vai trò phù hợp với phương án STABLE_NEARBY."));
        String v1 = propose(incident, "TRANSFER_VEHICLE", party("Đội cứu hộ xe tải (mẫu)")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PLAN_PENDING")).andReturn().getResponse().getContentAsString();
        long plan1 = ((Number) JsonPath.read(v1, "$.data.plans[0].id")).longValue();

        mvc.perform(post("/incident-plans/{id}/reject", plan1).header(HttpHeaders.AUTHORIZATION, manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/incident-plans/{id}/reject", plan1).header(HttpHeaders.AUTHORIZATION, manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Chi phí cứu hộ quá cao\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plans[0].status").value("REJECTED"));
        mvc.perform(post("/incident-plans/{id}/approve", plan1).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isConflict());

        String v2 = propose(incident, "CONTINUE", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.plans[1].version").value(2)).andReturn().getResponse().getContentAsString();
        long plan2 = ((Number) JsonPath.read(v2, "$.data.plans[1].id")).longValue();
        mvc.perform(post("/incident-plans/{id}/approve", plan2).header(HttpHeaders.AUTHORIZATION, coordinator)).andExpect(status().isForbidden());
        mvc.perform(post("/incident-plans/{id}/approve", plan2).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLAN_ACTIVE")).andExpect(jsonPath("$.data.plans[1].status").value("APPROVED"));

        act(coordinator, "/incidents/" + incident + "/resolve").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED")).andExpect(jsonPath("$.data.plans[1].status").value("DONE"));
        act(coordinator, "/incidents/" + incident + "/close").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CLOSED"));
        mvc.perform(get("/incidents").header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.id == " + incident + ")]").isEmpty());
    }

    @Test
    void horseIncidentNeedsRestAndFitConfirmationBeforeItIsResolved() throws Exception {
        long incident = id(report(leg, "HEALTH", "HORSE_CONDITION").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.horseName").value("Sao Mai")).andExpect(jsonPath("$.data.bookingCode").value("DEV-DIRECT-001")));
        act(escort, "/incidents/" + incident + "/fit-confirmation").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Phương án xử lý chưa được duyệt."));
        long plan = ((Number) JsonPath.read(propose(incident, "VET_TREATMENT", party("Phòng khám thú y ngựa (mẫu)")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.plans[0].id")).longValue();
        mvc.perform(post("/incident-plans/{id}/approve", plan).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isOk());

        act(escort, "/incidents/" + incident + "/fit-confirmation").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ngựa chưa được nghỉ và chăm sóc đủ 30 phút."));
        act(coordinator, "/incidents/" + incident + "/resolve").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa xác nhận ngựa đủ sức khỏe đi tiếp."));
        entityManager.flush();
        jdbc.update("UPDATE incident SET reported_at = now() - interval '40 minutes' WHERE id = ?", incident);
        entityManager.clear();
        act(escort, "/incidents/" + incident + "/fit-confirmation").andExpect(status().isOk()).andExpect(jsonPath("$.data.fitConfirmedAt").exists());
        act(coordinator, "/incidents/" + incident + "/resolve").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    @Test
    void costPayerFollowsTheCauseAndCustomerCostsRespectTheEmergencyLimit() throws Exception {
        long horseIncident = id(report(leg, "HEALTH", "HORSE_CONDITION"));
        long vehicleIncident = id(report(null, "VEHICLE_BREAKDOWN", "COMPANY_FAULT"));

        long repair = id(cost(vehicleIncident, "REPAIR", "3000000", false).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.payerType").value("COMPANY")));
        mvc.perform(post("/cost-items/{id}/approve", repair).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isOk());

        long vet = id(cost(horseIncident, "VET", "2000000", false).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.payerType").value("CUSTOMER")).andExpect(jsonPath("$.data.bookingCode").value("DEV-DIRECT-001")));
        // Chưa hỏi khách, đơn chưa có hạn mức khẩn cấp
        mvc.perform(post("/cost-items/{id}/approve", vet).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isConflict());
        entityManager.flush();
        jdbc.update("UPDATE booking SET emergency_spending_limit = 5000000 WHERE id = ?", booking);
        entityManager.clear();
        mvc.perform(post("/cost-items/{id}/approve", vet).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isOk());
        long more = id(cost(horseIncident, "VET", "4000000", false));
        mvc.perform(post("/cost-items/{id}/approve", more).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Khoản này do khách chịu, chưa hỏi ý kiến khách và vượt hạn mức chi khẩn cấp của đơn."));
        long consulted = id(cost(horseIncident, "VET", "4000000", true));
        mvc.perform(post("/cost-items/{id}/approve", consulted).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isOk());

        // Luồng A gắn khoản thu thêm: chỉ cho chi phí khách chịu đã duyệt
        long payment = jdbc.queryForObject("""
                INSERT INTO payment (booking_id, payment_type, amount, currency_code, payment_method, status)
                VALUES (?, 'EXTRA_CHARGE', 6000000, 'VND', 'VNPAY', 'PENDING') RETURNING id""", Long.class, booking);
        assertThatThrownBy(() -> costService.linkExtraPayment(List.of(repair), payment)).isInstanceOf(DispatchRuleException.class);
        assertThatThrownBy(() -> costService.linkExtraPayment(List.of(more), payment)).isInstanceOf(DispatchRuleException.class);
        costService.linkExtraPayment(List.of(vet, consulted), payment);
        entityManager.flush();
        assertThat(jdbc.queryForList("SELECT extra_charge_payment_id FROM cost_item WHERE id IN (?, ?)", Long.class, vet, consulted)).containsOnly(payment);
        mvc.perform(get("/bookings/{id}/cost-items", booking).header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void onlyCrewReportsAndOnlyStaffRolesActOnTheirSteps() throws Exception {
        String stranger = token(testData.staff(testData.unique("incident.stranger"), "ESCORT"), "ESCORT");
        mvc.perform(post("/incidents").header(HttpHeaders.AUTHORIZATION, stranger).contentType(MediaType.APPLICATION_JSON)
                .content(reportBody(null, "DELAY", "FORCE_MAJEURE"))).andExpect(status().isForbidden());
        long incident = id(report(null, "DELAY", "FORCE_MAJEURE"));
        mvc.perform(post("/incidents/{id}/plans", incident).header(HttpHeaders.AUTHORIZATION, escort).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolutionType\":\"CONTINUE\",\"instructions\":\"Đi tiếp\"}")).andExpect(status().isForbidden());
    }

    @Test
    void newHealthReportsNeedAHorseAndCompletedTripsRejectNewReports() throws Exception {
        report(null, "HEALTH", "HORSE_CONDITION").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sự cố sức khỏe phải chọn con ngựa."));
        jdbc.update("UPDATE vehicle_trip SET status = 'COMPLETED', actual_end_at = now() WHERE id = ?", trip);
        entityManager.clear();
        report(leg, "ACCIDENT", "COMPANY_FAULT").andExpect(status().isConflict());
    }

    @Test
    void legacyHealthAfterTripCompletionRequiresAVetReportForCoordinator() throws Exception {
        long incident = id(report(leg, "HEALTH", "HORSE_CONDITION").andExpect(status().isCreated()));
        long plan = ((Number) JsonPath.read(propose(incident, "CONTINUE", null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.plans[0].id")).longValue();
        act(manager, "/incident-plans/" + plan + "/approve").andExpect(status().isOk());
        entityManager.flush();
        jdbc.update("UPDATE incident SET horse_leg_id = null, reported_at = now() - interval '40 minutes' WHERE id = ?", incident);
        jdbc.update("UPDATE vehicle_trip SET status = 'COMPLETED', actual_end_at = now() WHERE id = ?", trip);
        entityManager.clear();

        addEvidence(incident, "INCIDENT_PHOTO");
        act(coordinator, "/incidents/" + incident + "/fit-confirmation").andExpect(status().isConflict());
        addEvidence(incident, "VET_REPORT");
        act(coordinator, "/incidents/" + incident + "/fit-confirmation").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiresFit").value(true)).andExpect(jsonPath("$.data.fitConfirmedAt").exists());
        cost(incident, "VET", "150000", true).andExpect(status().isCreated());
    }

    @Test
    void handoverTimeoutResolvesWithoutFitAndCannotUseReturnToHub() throws Exception {
        long incident = id(report(leg, "HANDOVER_TIMEOUT", "RECIPIENT_ABSENT").andExpect(status().isCreated()));
        propose(incident, "RETURN_TO_HUB", null).andExpect(status().isConflict());
        act(coordinator, "/incidents/" + incident + "/return-to-hub").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Xe giao thẳng vào hub, không cần bấm đưa về hub."));
        long plan = ((Number) JsonPath.read(propose(incident, "CONTINUE", null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.plans[0].id")).longValue();
        act(manager, "/incident-plans/" + plan + "/approve").andExpect(status().isOk());
        act(coordinator, "/incidents/" + incident + "/resolve").andExpect(status().isOk());
    }

    private void addEvidence(long incidentId, String type) {
        jdbc.update("""
                INSERT INTO document (document_type_id, file_url, file_name, uploaded_by, status, incident_id)
                SELECT id, 'test://' || code, code || '.pdf', ?, 'UPLOADED', ? FROM document_type WHERE code = ?
                """, coordinatorId, incidentId, type);
    }

    private ResultActions report(Long horseLeg, String type, String cause) throws Exception {
        return mvc.perform(post("/incidents").header(HttpHeaders.AUTHORIZATION, escort).contentType(MediaType.APPLICATION_JSON).content(reportBody(horseLeg, type, cause)));
    }

    private String reportBody(Long horseLeg, String type, String cause) {
        return "{\"vehicleTripId\":%d,\"horseLegId\":%s,\"incidentType\":\"%s\",\"causeType\":\"%s\",\"title\":\"Sự cố %s\",\"description\":\"Mô tả\",\"latitude\":11.2,\"longitude\":106.5}"
                .formatted(trip, horseLeg, type, cause, type);
    }

    private ResultActions propose(long incident, String type, Long handler) throws Exception {
        return mvc.perform(post("/incidents/{id}/plans", incident).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolutionType\":\"%s\",\"instructions\":\"Chỉ đạo\",\"estimatedCost\":1000000,\"handlerPartyId\":%s}".formatted(type, handler)));
    }

    private ResultActions cost(long incident, String category, String amount, boolean consulted) throws Exception {
        return mvc.perform(post("/cost-items").header(HttpHeaders.AUTHORIZATION, escort).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"%s\",\"description\":\"Chi phí\",\"amount\":%s,\"incidentId\":%d,\"customerConsulted\":%s,\"incurredAt\":\"2031-03-05T10:00:00Z\"}"
                        .formatted(category, amount, incident, consulted)));
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
