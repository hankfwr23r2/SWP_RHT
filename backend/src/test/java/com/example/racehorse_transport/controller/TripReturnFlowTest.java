package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.client.CloudinaryClient;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.scheduler.TripProgressNoticeScheduler;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.example.racehorse_transport.support.TripReturnPhotoFixtures;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class TripReturnFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private TestData testData;
    @Autowired private LocationRepository locations;
    @Autowired private JwtUtils jwt;
    @Autowired private Clock clock;
    @Autowired private TripProgressNoticeScheduler scheduler;
    @MockitoBean private CloudinaryClient storage;

    private Staff coordinator;
    private Staff driver;
    private Staff escort;
    private long home;
    private Instant plannedStart;

    @BeforeEach
    void setUp() {
        coordinator = staff("COORDINATOR");
        driver = staff("DRIVER");
        escort = staff("ESCORT");
        home = locations.findByCode("HUB-HCM").orElseThrow().getId();
        plannedStart = clock.instant().truncatedTo(ChronoUnit.SECONDS).minus(Duration.ofHours(4));
    }

    @Test
    void crewMustReachTheFinalHubAndAttachAnImageBeforeCompleting() throws Exception {
        long destination = locations.findByCode("HUB-DN").orElseThrow().getId();
        long trip = plan(testData.vehicle(2, 0).getId(), destination, plannedStart);
        assignDriver(trip, driver);
        start(trip, driver).andExpect(status().isOk()).andExpect(jsonPath("$.data.trip.returning").value(true));

        var photo = new MockMultipartFile("file", "return.jpg", "image/jpeg", new byte[] {1, 2, 3});
        upload(trip, driver, photo).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa tới điểm dừng cuối."));
        arriveAtFinalStop(trip);
        complete(trip).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chụp ảnh xe tại hub trước khi kết thúc lượt."));
        upload(trip, staff("DRIVER"), photo).andExpect(status().isForbidden());
        upload(trip, driver, new MockMultipartFile("file", "form.pdf", "application/pdf", new byte[] {1}))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.photo").isNotEmpty());

        when(storage.uploadFile(any(), eq("documents/vehicle_trip"))).thenReturn("https://cdn.example/return.jpg");
        upload(trip, driver, photo).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trip.returning").value(false))
                .andExpect(jsonPath("$.data.trip.returnPhotoAttached").value(true));
        upload(trip, driver, photo).andExpect(status().isOk());
        verify(storage).uploadFile(any(), eq("documents/vehicle_trip"));
        complete(trip).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.trip.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.trip.endLocationId").value(destination))
                .andExpect(jsonPath("$.data.trip.endVehicleCondition").value("NORMAL"));
        assertThat(jdbc.queryForObject("""
                SELECT d.uploaded_by FROM document d JOIN document_type t ON t.id = d.document_type_id
                WHERE d.vehicle_trip_id = ? AND t.code = 'VEHICLE_RETURN_PHOTO'
                """, Long.class, trip)).isEqualTo(driver.getUserId());
    }

    @Test
    void showsTheLateReturnAndNotifiesOnlyTheAssignedCoordinatorOnce() throws Exception {
        long trip = plan(testData.vehicle(2, 0).getId(), home, plannedStart);
        assignDriver(trip, driver);
        start(trip, driver).andExpect(status().isOk());
        entityManager.flush();
        jdbc.update("""
                UPDATE trip_stop SET actual_arrival_at = planned_arrival_at,
                    actual_departure_at = planned_departure_at + interval '20 minutes'
                WHERE vehicle_trip_id = ? AND sequence_number = 1
                """, trip);
        entityManager.clear();

        Instant eta = plannedStart.plus(Duration.ofMinutes(80));
        mvc.perform(get("/crew/vehicle-trips/{id}", trip).header(HttpHeaders.AUTHORIZATION, token(driver, "DRIVER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.trip.returning").value(true))
                .andExpect(jsonPath("$.data.trip.returnExpectedAt").value(eta.toString()))
                .andExpect(jsonPath("$.data.trip.returnLate").value(true));
        mvc.perform(get("/vehicle-trips/{id}", trip).header(HttpHeaders.AUTHORIZATION, token(coordinator, "COORDINATOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.returnLate").value(true));
        mvc.perform(get("/reports/trips/{id}", trip).header(HttpHeaders.AUTHORIZATION, token(staff("MANAGER"), "MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.returnExpectedAt").value(eta.toString()))
                .andExpect(jsonPath("$.data.summary.returnLate").value(true));

        scheduler.checkLateReturns();
        scheduler.checkLateReturns();
        entityManager.flush();
        assertThat(jdbc.queryForList("""
                SELECT r.user_id FROM notification n JOIN notification_recipient r ON r.notification_id = n.id
                WHERE n.title = 'Xe về trễ' AND n.message LIKE ? AND n.link_url = ?
                """, Long.class, "%" + code(trip) + "%", "/coordinator/dispatch?tripId=" + trip)).containsExactly(coordinator.getUserId());
        assertThat(jdbc.queryForObject("SELECT return_late_notified_at IS NOT NULL FROM vehicle_trip WHERE id = ?", Boolean.class, trip)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"vehicle", "driver", "escort"})
    void canPlanButCannotStartTheNextTripWhileAResourceIsStillOnAnOpenTrip(String busy) throws Exception {
        long vehicle = testData.vehicle(2, 0).getId();
        long current = plan(vehicle, home, plannedStart);
        assignDriver(current, driver);
        assign(current, escort, "ESCORT");
        start(current, driver).andExpect(status().isOk());
        arriveAtFinalStop(current);

        long nextVehicle = busy.equals("vehicle") ? vehicle : testData.vehicle(2, 0).getId();
        Staff nextDriver = busy.equals("driver") ? driver : staff("DRIVER");
        long next = plan(nextVehicle, home, plannedStart.plus(Duration.ofDays(1)));
        assignDriver(next, nextDriver);
        if (busy.equals("escort")) assign(next, escort, "ESCORT");
        String field = busy.equals("vehicle") ? "vehicleId" : "staffId";
        start(next, nextDriver).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data." + field, containsString(code(current))));

        TripReturnPhotoFixtures.attach(jdbc, current);
        complete(current).andExpect(status().isOk());
        start(next, nextDriver).andExpect(status().isOk()).andExpect(jsonPath("$.data.trip.status").value("IN_PROGRESS"));
    }

    private long plan(long vehicle, long destination, Instant start) throws Exception {
        String body = """
                {"tripType":"TRANSFER","vehicleId":%d,"stops":[
                {"stopType":"HUB","locationId":%d,"plannedArrivalAt":"%s","plannedDepartureAt":"%s"},
                {"stopType":"HUB","locationId":%d,"plannedArrivalAt":"%s","plannedDepartureAt":"%s"}]}
                """.formatted(vehicle, home, start, start.plusSeconds(600), destination,
                start.plusSeconds(3600), start.plusSeconds(4200));
        var response = mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, token(coordinator, "COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse();
        return ((Number) JsonPath.read(response.getContentAsString(), "$.data.id")).longValue();
    }

    private void assignDriver(long trip, Staff member) throws Exception {
        assign(trip, member, "DRIVER");
        long id = jdbc.queryForObject("SELECT id FROM crew_assignment WHERE vehicle_trip_id = ? AND staff_id = ?",
                Long.class, trip, member.getUserId());
        mvc.perform(post("/crew-assignments/{id}/acknowledge", id)
                .header(HttpHeaders.AUTHORIZATION, token(member, "DRIVER"))).andExpect(status().isOk());
    }

    private void assign(long trip, Staff member, String role) throws Exception {
        mvc.perform(post("/vehicle-trips/{id}/crew-assignments", trip).header(HttpHeaders.AUTHORIZATION, token(coordinator, "COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"staffId\":%d,\"crewRole\":\"%s\"}".formatted(member.getUserId(), role))).andExpect(status().isCreated());
    }

    private ResultActions start(long trip, Staff member) throws Exception {
        return mvc.perform(post("/vehicle-trips/{id}/start", trip).header(HttpHeaders.AUTHORIZATION, token(member, "DRIVER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"earlyReason\":\"Tổ lái đã sẵn sàng\"}"));
    }

    private void arriveAtFinalStop(long trip) throws Exception {
        var ordered = jdbc.queryForList("SELECT id FROM trip_stop WHERE vehicle_trip_id = ? ORDER BY sequence_number", Long.class, trip);
        String authorization = token(driver, "DRIVER");
        mvc.perform(post("/trip-stops/{id}/arrive", ordered.getFirst()).header(HttpHeaders.AUTHORIZATION, authorization)).andExpect(status().isOk());
        mvc.perform(post("/trip-stops/{id}/depart", ordered.getFirst()).header(HttpHeaders.AUTHORIZATION, authorization)).andExpect(status().isOk());
        mvc.perform(post("/trip-stops/{id}/arrive", ordered.getLast()).header(HttpHeaders.AUTHORIZATION, authorization)).andExpect(status().isOk());
    }

    private ResultActions upload(long trip, Staff member, MockMultipartFile photo) throws Exception {
        return mvc.perform(multipart("/vehicle-trips/{id}/return-photo", trip).file(photo)
                .header(HttpHeaders.AUTHORIZATION, token(member, "DRIVER")));
    }

    private ResultActions complete(long trip) throws Exception {
        return mvc.perform(post("/vehicle-trips/{id}/complete", trip).header(HttpHeaders.AUTHORIZATION, token(driver, "DRIVER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"endVehicleCondition\":\"NORMAL\"}"));
    }

    private Staff staff(String role) {
        return testData.staff(testData.unique("return." + role), role);
    }

    private String token(Staff member, String role) {
        return "Bearer " + jwt.generateToken(member.getUser().getUsername(), List.of(role));
    }

    private String code(long trip) {
        return jdbc.queryForObject("SELECT code FROM vehicle_trip WHERE id = ?", String.class, trip);
    }
}
