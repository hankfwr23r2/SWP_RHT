package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
class TripPlanEditFlowTest {

    private static final Instant START = Instant.parse("2031-03-03T00:00:00Z");
    private static final String CUSTOMER_NOTICE = "Kế hoạch vận chuyển đã thay đổi";
    private static final String CREW_NOTICE = "Kế hoạch lượt xe đã thay đổi";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwt;
    @Autowired private LocationRepository locations;
    @Autowired private EntityManager entityManager;

    private String coordinator;
    private long hub;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        coordinator = bearer(staff("COORDINATOR"), "COORDINATOR");
        hub = locations.findByCode("HUB-HCM").orElseThrow().getId();
        vehicle = testData.vehicle(4, 0);
    }

    @Test
    void swapsVehicleAndShiftsTripStopsAndLegsThenNotifiesCrewAndCustomerOnce() throws Exception {
        Pickup pickup = pickupWithHorses();
        Staff driver = staff("DRIVER");
        Staff escort = staff("ESCORT");
        assignCrew(pickup.trip(), driver, "DRIVER");
        assignCrew(pickup.trip(), escort, "ESCORT");
        entityManager.flush();
        jdbc.update("UPDATE horse_leg SET stall_position = 4 WHERE vehicle_trip_id = ? AND stall_position = 2", pickup.trip());
        entityManager.clear();
        Vehicle replacement = testData.vehicle(2, 0);
        Instant start = pickup.start().plus(1, ChronoUnit.HOURS);
        String change = """
                {"vehicleId":%d,"plannedStartAt":"%s"}
                """.formatted(replacement.getId(), start);

        edit(pickup.trip(), change).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vehicleId").value(replacement.getId()))
                .andExpect(jsonPath("$.data.plannedStartAt").value(start.toString()))
                .andExpect(jsonPath("$.data.plannedEndAt").value(start.plus(3, ChronoUnit.HOURS).toString()))
                .andExpect(jsonPath("$.data.stops[1].id").value(pickup.board()))
                .andExpect(jsonPath("$.data.stops[1].plannedArrivalAt").value(start.plus(1, ChronoUnit.HOURS).toString()))
                .andExpect(jsonPath("$.data.crew.length()").value(2));
        entityManager.flush();
        entityManager.clear();
        assertThat(jdbc.queryForList("SELECT planned_start_at FROM horse_leg WHERE vehicle_trip_id = ?", Timestamp.class, pickup.trip()))
                .hasSize(2).allMatch(at -> at.toInstant().equals(start.plus(90, ChronoUnit.MINUTES)));
        assertThat(jdbc.queryForList("SELECT planned_end_at FROM horse_leg WHERE vehicle_trip_id = ?", Timestamp.class, pickup.trip()))
                .hasSize(2).allMatch(at -> at.toInstant().equals(start.plus(3, ChronoUnit.HOURS)));
        assertThat(jdbc.queryForList("SELECT stall_position FROM horse_leg WHERE vehicle_trip_id = ?", Integer.class, pickup.trip()))
                .containsExactlyInAnyOrder(1, 2);

        edit(pickup.trip(), change).andExpect(status().isOk());
        entityManager.flush();
        assertThat(noticeCount(pickup.customer(), CUSTOMER_NOTICE)).isEqualTo(1);
        assertThat(noticeCount(driver.getUserId(), CREW_NOTICE)).isEqualTo(1);
        assertThat(noticeCount(escort.getUserId(), CREW_NOTICE)).isEqualTo(1);
    }

    @Test
    void editsOneManualStopAndUpdatesOnlyTheDependentLegTimes() throws Exception {
        Pickup pickup = pickupWithHorses();
        var arrival = pickup.start().plus(70, ChronoUnit.MINUTES);
        var departure = pickup.start().plus(100, ChronoUnit.MINUTES);
        edit(pickup.trip(), stopEdit(pickup.board(), arrival, departure)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.manualStopTimesEditable").value(true))
                .andExpect(jsonPath("$.data.plannedStartAt").value(pickup.start().toString()))
                .andExpect(jsonPath("$.data.stops[1].plannedArrivalAt").value(arrival.toString()))
                .andExpect(jsonPath("$.data.stops[1].plannedDepartureAt").value(departure.toString()))
                .andExpect(jsonPath("$.data.stops[2].plannedArrivalAt").value(pickup.start().plus(3, ChronoUnit.HOURS).toString()));
        entityManager.flush();
        assertThat(jdbc.queryForList("SELECT planned_start_at FROM horse_leg WHERE vehicle_trip_id = ?", Timestamp.class, pickup.trip()))
                .hasSize(2).allMatch(at -> at.toInstant().equals(departure));
    }

    @Test
    void rejectsAReplacementThatCannotCarryAllHorses() throws Exception {
        Pickup pickup = pickupWithHorses();
        edit(pickup.trip(), vehicleEdit(testData.vehicle(1, 0))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.bookingHorseId", containsString("sẽ cần 2 chỗ")));
    }

    @Test
    void rejectsAnInactiveReplacement() throws Exception {
        long trip = emptyTrip(vehicle, START, 2);
        Vehicle replacement = testData.vehicle(4, 0);
        replacement.setStatus(VehicleStatus.MAINTENANCE);
        entityManager.flush();
        edit(trip, vehicleEdit(replacement)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.vehicleId", containsString("MAINTENANCE")));
    }

    @Test
    void rejectsOverlappingVehicleScheduleAndLeavesThePlanAndNoticesAsBefore() throws Exception {
        long trip = emptyTrip(vehicle, START, 2);
        emptyTrip(vehicle, START.plus(4, ChronoUnit.HOURS), 2);
        Staff driver = staff("DRIVER");
        assignCrew(trip, driver, "DRIVER");

        edit(trip, shift(START.plus(3, ChronoUnit.HOURS))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.vehicleId", containsString("trùng giờ")));
        mvc.perform(get("/vehicle-trips/{id}", trip).header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.plannedStartAt").value(START.toString()));
        assertThat(noticeCount(driver.getUserId(), CREW_NOTICE)).isZero();
    }

    @Test
    void rejectsOverlappingCrewScheduleOnADifferentVehicle() throws Exception {
        Staff driver = staff("DRIVER");
        long trip = emptyTrip(vehicle, START, 2);
        long other = emptyTrip(testData.vehicle(4, 0), START.plus(4, ChronoUnit.HOURS), 2);
        assignCrew(trip, driver, "DRIVER");
        assignCrew(other, driver, "DRIVER");

        edit(trip, shift(START.plus(3, ChronoUnit.HOURS))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.staffId", containsString("trùng giờ")));
    }

    @Test
    void rejectsACrewScheduleOverTheCombinedDailyDrivingLimit() throws Exception {
        Staff driver = staff("DRIVER");
        long trip = emptyTrip(vehicle, START.plus(1, ChronoUnit.DAYS), 4);
        long other = emptyTrip(testData.vehicle(4, 0), START, 7);
        assignCrew(trip, driver, "DRIVER");
        assignCrew(other, driver, "DRIVER");

        edit(trip, shift(START.plus(8, ChronoUnit.HOURS))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.staffId", containsString("quá giới hạn 10 giờ")));
    }

    @Test
    void rejectsPickupAfterTheHubDeadline() throws Exception {
        Pickup pickup = pickupWithHorses();
        edit(pickup.trip(), shift(pickup.start().plus(5, ChronoUnit.HOURS))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.alightTripStopId", containsString("sau hạn có mặt")));
    }

    @Test
    void rejectsManualStopTimeThatKeepsTheHorseOnboardTooLong() throws Exception {
        Pickup pickup = pickupWithHorses();
        // Quy chế đội xe Q2: ngồi xe quá 4 giờ phải có trạm nghỉ, nên giờ sửa tay để ngựa ngồi quá lâu bị chặn
        Instant arrival = pickup.start().plus(9, ChronoUnit.HOURS);
        edit(pickup.trip(), stopEdit(pickup.alight(), arrival, arrival)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.places", containsString("trạm nghỉ")));
    }

    @Test
    void anotherCoordinatorCannotEditTheTrip() throws Exception {
        long trip = emptyTrip(vehicle, START, 2);
        String other = bearer(staff("COORDINATOR"), "COORDINATOR");
        mvc.perform(patch("/vehicle-trips/{id}", trip).header(HttpHeaders.AUTHORIZATION, other)
                        .contentType(MediaType.APPLICATION_JSON).content(shift(START.plus(1, ChronoUnit.HOURS))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTripAlreadyUnderwayCannotBeEdited() throws Exception {
        long trip = emptyTrip(vehicle, START, 2);
        entityManager.flush();
        jdbc.update("UPDATE vehicle_trip SET status = 'IN_PROGRESS', actual_start_at = ? WHERE id = ?", Timestamp.from(START), trip);
        entityManager.clear();

        edit(trip, shift(START.plus(1, ChronoUnit.HOURS))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.status").exists());
    }

    @Test
    void linehaulCanChangeVehicleAndKeepsTheDepartureSchedule() throws Exception {
        var departure = testData.departure("HCM-DN", LocalDate.of(2031, 3, 3), 9);
        var manager = staff("MANAGER");
        jdbc.update("UPDATE departure SET run_confirmed_at = now(), run_confirmed_by = ? WHERE id = ?", manager.getUserId(), departure.getId());
        entityManager.clear();
        String body = create("/vehicle-trips", """
                {"tripType":"LINEHAUL","vehicleId":%d,"departureId":%d}
                """.formatted(vehicle.getId(), departure.getId()));
        long trip = id(body, "$.data.id");
        String start = JsonPath.read(body, "$.data.plannedStartAt");
        Vehicle replacement = testData.vehicle(4, 0);

        edit(trip, vehicleEdit(replacement)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vehicleId").value(replacement.getId()))
                .andExpect(jsonPath("$.data.plannedStartAt").value(start))
                .andExpect(jsonPath("$.data.stops[0].plannedArrivalAt").value(start));
        edit(trip, shift(START)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.plannedStartAt", containsString("chỉ được đổi xe")));
    }

    @Test
    void calculatedTripRejectsAnIndividualStopEdit() throws Exception {
        long danang = locations.findByCode("HUB-DN").orElseThrow().getId();
        String body = create("/vehicle-trips/planned", """
                {"tripType":"TRANSFER","vehicleId":%d,"places":[{"locationId":%d},{"locationId":%d}],"bookingHorseIds":[],"startAt":"%s"}
                """.formatted(vehicle.getId(), hub, danang, START));
        assertThat((Boolean) JsonPath.read(body, "$.data.manualStopTimesEditable")).isFalse();
        long stop = id(body, "$.data.stops[0].id");
        Instant later = START.plus(1, ChronoUnit.HOURS);
        edit(id(body, "$.data.id"), stopEdit(stop, later, later)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.stops", containsString("giờ nhập tay")));
    }

    private Pickup pickupWithHorses() throws Exception {
        long booking = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DOMESTIC-001'", Long.class);
        long customer = jdbc.queryForObject("SELECT customer_id FROM booking WHERE id = ?", Long.class, booking);
        long address = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'PICKUP'", Long.class, booking);
        Instant departure = jdbc.queryForObject("""
                SELECT d.planned_departure_at FROM departure d JOIN booking b ON b.departure_id = d.id WHERE b.id = ?
                """, Timestamp.class, booking).toInstant();
        Instant start = departure.minus(10, ChronoUnit.HOURS);
        String body = create("/vehicle-trips", """
                {"tripType":"PICKUP","vehicleId":%d,"stops":[%s,%s,%s]}
                """.formatted(vehicle.getId(), hubStop(start), """
                        {"bookingAddressId":%d,"stopType":"PICKUP","plannedArrivalAt":"%s","plannedDepartureAt":"%s"}
                        """.formatted(address, start.plus(1, ChronoUnit.HOURS), start.plus(90, ChronoUnit.MINUTES)), hubStop(start.plus(3, ChronoUnit.HOURS))));
        Pickup pickup = new Pickup(id(body, "$.data.id"), id(body, "$.data.stops[1].id"), id(body, "$.data.stops[2].id"), start, customer);
        for (long horse : jdbc.queryForList("SELECT id FROM booking_horse WHERE booking_id = ? ORDER BY id", Long.class, booking)) {
            mvc.perform(post("/vehicle-trips/{id}/horse-legs", pickup.trip()).header(HttpHeaders.AUTHORIZATION, coordinator)
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"bookingHorseId":%d,"boardTripStopId":%d,"alightTripStopId":%d}
                                    """.formatted(horse, pickup.board(), pickup.alight())))
                    .andExpect(status().isCreated());
        }
        return pickup;
    }

    private long emptyTrip(Vehicle selected, Instant start, int hours) throws Exception {
        return id(create("/vehicle-trips", """
                {"tripType":"TRANSFER","vehicleId":%d,"stops":[%s,%s]}
                """.formatted(selected.getId(), hubStop(start), hubStop(start.plus(hours, ChronoUnit.HOURS)))), "$.data.id");
    }

    private String hubStop(Instant at) {
        return """
                {"locationId":%d,"stopType":"HUB","plannedArrivalAt":"%s","plannedDepartureAt":"%s"}
                """.formatted(hub, at, at);
    }

    private String create(String path, String body) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions edit(long trip, String body) throws Exception {
        return mvc.perform(patch("/vehicle-trips/{id}", trip).header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assignCrew(long trip, Staff member, String role) throws Exception {
        mvc.perform(post("/vehicle-trips/{id}/crew-assignments", trip).header(HttpHeaders.AUTHORIZATION, coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"staffId":%d,"crewRole":"%s"}
                                """.formatted(member.getUserId(), role))).andExpect(status().isCreated());
    }

    private int noticeCount(long user, String title) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM notification n JOIN notification_recipient r ON r.notification_id = n.id
                WHERE r.user_id = ? AND n.title = ?
                """, Integer.class, user, title);
    }

    private Staff staff(String role) {
        return testData.staff(testData.unique("trip.edit." + role.toLowerCase(java.util.Locale.ROOT)), role);
    }

    private String bearer(Staff staff, String role) {
        return "Bearer " + jwt.generateToken(staff.getUser().getUsername(), List.of(role));
    }

    private static String shift(Instant at) {
        return "{\"plannedStartAt\":\"%s\"}".formatted(at);
    }

    private static String vehicleEdit(Vehicle vehicle) {
        return "{\"vehicleId\":%d}".formatted(vehicle.getId());
    }

    private static String stopEdit(long id, Instant arrival, Instant departure) {
        return """
                {"stops":[{"stopId":%d,"plannedArrivalAt":"%s","plannedDepartureAt":"%s"}]}
                """.formatted(id, arrival, departure);
    }

    private static long id(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private record Pickup(long trip, long board, long alight, Instant start, long customer) {}
}
