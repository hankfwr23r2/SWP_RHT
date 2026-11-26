package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.client.CloudinaryClient;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
    @MockitoBean private CloudinaryClient cloudinary;

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
        jdbc.update("UPDATE booking SET emergency_spending_limit = 1000000 WHERE id = ?", booking);
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

        act(escort, "/incidents/" + incident + "/rest-start").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Sự cố này không yêu cầu xác nhận đủ sức."));
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
        mvc.perform(get("/notifications/mine").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + jwtUtils.generateToken("customer1.dev", List.of("CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == 'Sự cố vận chuyển')]").isNotEmpty());
        mvc.perform(get("/notifications/mine").header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == 'Sự cố mới' && @.linkUrl == '/coordinator/incidents?incidentId="
                        + incident + "')]").isNotEmpty());
        managerNotice(incident, "Sự cố mới");
        String restPath = "/incidents/" + incident + "/rest-start";
        String fitPath = "/incidents/" + incident + "/fit-confirmation";
        act(escort, restPath).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Phương án xử lý chưa được duyệt."));
        act(escort, fitPath).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Phương án xử lý chưa được duyệt."));
        long plan = ((Number) JsonPath.read(propose(incident, "VET_TREATMENT", party("Phòng khám thú y ngựa (mẫu)")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.plans[0].id")).longValue();
        managerNotice(incident, "Phương án sự cố chờ duyệt");
        mvc.perform(post("/incident-plans/{id}/approve", plan).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isOk());

        Staff driverStaff = testData.staff(testData.unique("incident.driver"), "DRIVER");
        String driver = token(driverStaff, "DRIVER");
        String otherEscort = token(testData.staff(testData.unique("incident.other-escort"), "ESCORT"), "ESCORT");
        jdbc.update("""
                INSERT INTO crew_assignment (vehicle_trip_id, staff_id, crew_role, status)
                VALUES (?, ?, 'DRIVER', 'ACKNOWLEDGED')
                """, trip, driverStaff.getUserId());
        entityManager.flush();
        jdbc.update("UPDATE incident SET reported_at = now() - interval '40 minutes' WHERE id = ?", incident);
        entityManager.clear();
        act(escort, fitPath).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa bắt đầu cho ngựa nghỉ."));
        act(driver, restPath).andExpect(status().isForbidden());
        act(otherEscort, restPath).andExpect(status().isForbidden());

        String rest = act(escort, restPath).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Instant startedAt = Instant.parse(JsonPath.read(rest, "$.data.restStartedAt"));
        assertThat(Instant.parse(JsonPath.read(rest, "$.data.fitEligibleAt"))).isEqualTo(startedAt.plusSeconds(1800));
        act(escort, restPath).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Đã bắt đầu cho ngựa nghỉ."));
        act(escort, fitPath).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ngựa chưa được nghỉ và chăm sóc đủ 30 phút."));
        act(coordinator, "/incidents/" + incident + "/resolve").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa xác nhận ngựa đủ sức khỏe đi tiếp."));

        backdateRest(incident);
        addEvidence(incident, "INCIDENT_PHOTO");
        act(escort, fitPath).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cần ảnh con ngựa trước khi xác nhận đủ sức."));
        uploadFitPhoto(incident, otherEscort, "image/jpeg").andExpect(status().isForbidden());
        uploadFitPhoto(incident, escort, "application/pdf").andExpect(status().isBadRequest());
        when(cloudinary.uploadFile(any(), anyString())).thenReturn("https://cdn.example/fit-check.jpg");
        uploadFitPhoto(incident, escort, "image/jpeg").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.documentTypeCode == 'FIT_CHECK_PHOTO')]").isNotEmpty());
        act(driver, fitPath).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Chỉ hộ tống của lượt xe được xác nhận ngựa đủ sức đi tiếp."));
        act(otherEscort, fitPath).andExpect(status().isForbidden());
        act(escort, fitPath).andExpect(status().isOk()).andExpect(jsonPath("$.data.fitConfirmedAt").exists());
        act(coordinator, "/incidents/" + incident + "/resolve").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cần thực hiện phương án sự cố trước khi báo đã xử lý xong."));
        entityManager.flush();
        jdbc.update("UPDATE trip_stop SET actual_arrival_at = now() WHERE vehicle_trip_id = ? AND sequence_number = 1", trip);
        entityManager.clear();
        mvc.perform(post("/incidents/{id}/vet-result", incident).header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Đã khám và cho nghỉ\"}")).andExpect(status().isOk());
        act(coordinator, "/incidents/" + incident + "/resolve").andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    @Test
    void customerCanAcceptOnlyTheirPlanAndConfirmedCompanyFaultReassignsPendingCosts() throws Exception {
        String customerName = jdbc.queryForObject("""
                SELECT u.username FROM app_user u JOIN booking b ON b.customer_id = u.id WHERE b.id = ?
                """, String.class, booking);
        String customer = "Bearer " + jwtUtils.generateToken(customerName, List.of("CUSTOMER"));
        var otherCustomer = testData.customer(testData.unique("incident.other-customer"));
        String other = "Bearer " + jwtUtils.generateToken(otherCustomer.getUser().getUsername(), List.of("CUSTOMER"));
        long incident = id(report(leg, "HEALTH", "HORSE_CONDITION"));
        long declined = id(cost(incident, "VET", "500000", false).andExpect(status().isCreated()));
        long accepted = id(cost(incident, "VET", "1500000", false).andExpect(status().isCreated()));
        mvc.perform(get("/bookings/{id}/customer-cost-items", booking + 1000000)
                        .header(HttpHeaders.AUTHORIZATION, customer)).andExpect(status().isNotFound());
        mvc.perform(get("/bookings/{id}/customer-cost-items", booking)
                        .header(HttpHeaders.AUTHORIZATION, other)).andExpect(status().isNotFound());
        act(other, "/cost-items/" + accepted + "/accept").andExpect(status().isNotFound());
        act(customer, "/cost-items/" + declined + "/decline").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerAgreed").value(false));
        act(customer, "/cost-items/" + accepted + "/accept").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerAgreed").value(true));

        entityManager.flush();
        jdbc.update("UPDATE booking SET emergency_spending_limit = 500000 WHERE id = ?", booking);
        entityManager.clear();
        long plan = ((Number) JsonPath.read(propose(incident, "CONTINUE", null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.plans[0].id")).longValue();
        act(manager, "/incident-plans/" + plan + "/approve").andExpect(status().isConflict());
        act(other, "/incident-plans/" + plan + "/accept").andExpect(status().isNotFound());
        act(customer, "/incident-plans/" + plan + "/accept").andExpect(status().isOk());
        act(manager, "/incident-plans/" + plan + "/approve").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedCauseType").value("HORSE_CONDITION"));
        act(manager, "/cost-items/" + accepted + "/approve").andExpect(status().isOk());
        act(manager, "/cost-items/" + declined + "/approve").andExpect(status().isConflict());

        long next = ((Number) JsonPath.read(propose(incident, "CONTINUE", null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.plans[1].id")).longValue();
        mvc.perform(post("/incident-plans/{id}/approve", next).header(HttpHeaders.AUTHORIZATION, manager)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"confirmedCauseType":"COMPANY_FAULT"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.confirmedCauseType").value("COMPANY_FAULT"));
        act(manager, "/cost-items/" + declined + "/mark-recovered").andExpect(status().isConflict());
        act(manager, "/cost-items/" + declined + "/approve").andExpect(status().isOk())
                 .andExpect(jsonPath("$.data.payerType").value("COMPANY"));
        mvc.perform(get("/incidents/{id}/cost-items", incident).header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + accepted + ")].payerType").value(org.hamcrest.Matchers.contains("CUSTOMER")));
    }

    @Test
    void costPayerFollowsTheCauseAndCustomerCostsRespectTheEmergencyLimit() throws Exception {
        long horseIncident = id(report(leg, "HEALTH", "HORSE_CONDITION"));
        long vehicleIncident = id(report(null, "VEHICLE_BREAKDOWN", "COMPANY_FAULT"));

        long repair = id(cost(vehicleIncident, "REPAIR", "3000000", false).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.payerType").value("COMPANY")));
        managerNotice(vehicleIncident, "Chi phí sự cố chờ duyệt");
        mvc.perform(post("/cost-items/{id}/approve", repair).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isOk());

        long vet = id(cost(horseIncident, "VET", "2000000", false).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.payerType").value("CUSTOMER")).andExpect(jsonPath("$.data.bookingCode").value("DEV-DIRECT-001")));
        // Chưa chốt nguyên nhân và chưa có phản hồi của khách
        mvc.perform(post("/cost-items/{id}/approve", vet).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isConflict());
        entityManager.flush();
        jdbc.update("UPDATE booking SET emergency_spending_limit = 5000000 WHERE id = ?", booking);
        jdbc.update("UPDATE cost_item SET customer_response_due_at = now() - interval '1 minute' WHERE id = ?", vet);
        entityManager.clear();
        long costPlan = ((Number) JsonPath.read(propose(horseIncident, "CONTINUE", null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.data.plans[0].id")).longValue();
        act(manager, "/incident-plans/" + costPlan + "/approve").andExpect(status().isOk());

        entityManager.clear();
        mvc.perform(post("/cost-items/{id}/approve", vet).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isOk());
        long more = id(cost(horseIncident, "VET", "4000000", false));
        entityManager.flush();
        jdbc.update("UPDATE cost_item SET customer_response_due_at = now() - interval '1 minute' WHERE id = ?", more);
        entityManager.clear();
        mvc.perform(post("/cost-items/{id}/approve", more).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Khoản này do khách chịu, chưa hỏi ý kiến khách và vượt hạn mức chi khẩn cấp của đơn."));
        long consulted = id(cost(horseIncident, "VET", "4000000", true));
        mvc.perform(post("/cost-items/{id}/approve", consulted).header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isConflict());
        mvc.perform(post("/cost-items/{id}/phone-consent", consulted)
                        .header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Customer approved the cost by phone.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.customerConsulted").value(true));
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
    void staffListsIncidentsBySeverityThenNewestReportAndChangesAreAudited() throws Exception {
        mvc.perform(get("/incidents/severity-defaults").header(HttpHeaders.AUTHORIZATION, escort))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.HEALTH").value("HIGH"))
                .andExpect(jsonPath("$.data.DOCUMENT").value("MEDIUM")).andExpect(jsonPath("$.data.DELAY").value("LOW"));
        long olderHigh = id(report(leg, "HEALTH", "HORSE_CONDITION").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.severity").value("HIGH")));
        long newerHigh = id(report(leg, "ACCIDENT", "COMPANY_FAULT").andExpect(status().isCreated()));
        long medium = id(report(leg, "DOCUMENT", "CUSTOMER_DOCS").andExpect(status().isCreated()));
        long low = id(report(leg, "DELAY", "FORCE_MAJEURE").andExpect(status().isCreated()));
        jdbc.update("UPDATE incident SET reported_at = now() - interval '4 minutes' WHERE id = ?", olderHigh);
        jdbc.update("UPDATE incident SET reported_at = now() - interval '3 minutes' WHERE id = ?", newerHigh);
        jdbc.update("UPDATE incident SET reported_at = now() - interval '2 minutes' WHERE id = ?", medium);
        entityManager.clear();

        assertIncidentOrder("/incidents", manager, newerHigh, olderHigh, medium, low);
        assertIncidentOrder("/vehicle-trips/" + trip + "/incidents", coordinator, newerHigh, olderHigh, medium, low);
        changeSeverity(low, escort, "{\"severity\":\"HIGH\"}").andExpect(status().isForbidden());
        changeSeverity(low, coordinator, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.severity").isNotEmpty());
        changeSeverity(low, coordinator, "{\"severity\":\"HIGH\"}").andExpect(status().isOk());
        assertIncidentOrder("/incidents", manager, low, newerHigh, olderHigh, medium);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log WHERE target_table = 'incident' AND target_id = ?
                    AND action = 'CHANGE_INCIDENT_SEVERITY' AND actor_user_id = ?
                    AND old_data ->> 'severity' = 'LOW' AND new_data ->> 'severity' = 'HIGH'
                """, Integer.class, low, coordinatorId)).isEqualTo(1);
        changeSeverity(low, manager, "{\"severity\":\"MEDIUM\"}").andExpect(status().isOk());
        mvc.perform(get("/incidents/{id}", low).header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.severity").value("MEDIUM"))
                .andExpect(jsonPath("$.data.blocksTrip").value(false));
    }

    private ResultActions changeSeverity(long incident, String token, String body) throws Exception {
        return mvc.perform(patch("/incidents/{id}/severity", incident).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assertIncidentOrder(String path, String token, Long... expected) throws Exception {
        String body = mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$.data[*].id");
        List<Long> idsOnTestTrip = ids.stream().map(Number::longValue).filter(List.of(expected)::contains).toList();
        assertThat(idsOnTestTrip).containsExactly(expected);
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
        jdbc.update("UPDATE incident SET horse_leg_id = null WHERE id = ?", incident);
        jdbc.update("UPDATE vehicle_trip SET status = 'COMPLETED', actual_end_at = now() WHERE id = ?", trip);
        entityManager.clear();

        addEvidence(incident, "INCIDENT_PHOTO");
        String fitPath = "/incidents/" + incident + "/fit-confirmation";
        act(coordinator, fitPath).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cần tải kết quả thú y trước khi xác nhận thay."));
        addEvidence(incident, "VET_REPORT");
        // Tổ lái không còn trên lượt nên không ai bấm bắt đầu nghỉ: điều phối xác nhận thay đếm từ lúc báo sự cố
        act(coordinator, fitPath).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ngựa chưa được nghỉ và chăm sóc đủ 30 phút."));
        entityManager.flush();
        jdbc.update("UPDATE incident SET reported_at = now() - interval '40 minutes' WHERE id = ?", incident);
        entityManager.clear();
        act(coordinator, fitPath).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiresFit").value(true)).andExpect(jsonPath("$.data.fitConfirmedAt").exists());
        cost(incident, "VET", "150000", true).andExpect(status().isCreated());
    }

    @Test
    void handoverTimeoutResolvesWithoutFitAndCannotUseReturnToHub() throws Exception {
        report(leg, "HANDOVER_TIMEOUT", "RECIPIENT_ABSENT").andExpect(status().isBadRequest());
        long incident = jdbc.queryForObject("""
                INSERT INTO incident (vehicle_trip_id, horse_leg_id, booking_id, incident_type, severity, cause_type,
                    confirmed_cause_type, cause_confirmed_at, title, description, reported_by, status)
                VALUES (?, ?, ?, 'HANDOVER_TIMEOUT', 'MEDIUM', 'RECIPIENT_ABSENT', 'RECIPIENT_ABSENT', now(),
                    'Delivery timeout', 'Customer did not meet the vehicle', ?, 'REPORTED')
                RETURNING id
                """, Long.class, trip, leg, booking, coordinatorId);
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

    private void backdateRest(long incidentId) {
        entityManager.flush();
        jdbc.update("UPDATE incident SET rest_started_at = now() - interval '40 minutes' WHERE id = ?", incidentId);
        entityManager.clear();
    }

    private ResultActions uploadFitPhoto(long incidentId, String token, String contentType) throws Exception {
        var file = new MockMultipartFile("file", "sao-mai.jpg", contentType, new byte[] {1, 2, 3});
        return mvc.perform(multipart("/incidents/{id}/photos", incidentId).file(file).param("documentType", "FIT_CHECK_PHOTO")
                .header(HttpHeaders.AUTHORIZATION, token));
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
    private void managerNotice(long incidentId, String title) throws Exception {
        mvc.perform(get("/notifications/mine").header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.title == '" + title + "' && @.linkUrl == '/manager/incidents?incidentId="
                        + incidentId + "')]", org.hamcrest.Matchers.hasSize(1)));
    }
}
