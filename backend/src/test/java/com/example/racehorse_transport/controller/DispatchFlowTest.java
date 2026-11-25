package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// B1: điều phối lập lượt xe, phân công tổ lái; tài xế, hộ tống xác nhận nhận việc
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class DispatchFlowTest {

    // Chuyến HCM-DN đột xuất lúc 06:00 UTC; tuyến có 8 điểm, điểm cuối HUB-DN tới sau 1781 phút
    private static final LocalDate DAY = LocalDate.of(2031, 3, 3);
    private static final Instant LINEHAUL_START = Instant.parse("2031-03-03T06:00:00Z");
    private static final Instant LINEHAUL_END = Instant.parse("2031-03-04T11:41:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private LocationRepository locations;
    @Autowired private VehicleRepository vehicles;
    @Autowired private EntityManager entityManager;

    private String coordinator;
    private Vehicle vehicle;
    private Departure departure;
    private long hcm;
    private long danang;

    @BeforeEach
    void setUp() {
        coordinator = bearer(testData.staff(testData.unique("dispatch.coordinator"), "COORDINATOR"), "COORDINATOR");
        vehicle = testData.vehicle(4, 1);
        departure = testData.departure("HCM-DN", DAY, 9);
        hcm = locations.findByCode("HUB-HCM").orElseThrow().getId();
        danang = locations.findByCode("HUB-DN").orElseThrow().getId();
    }

    @Test
    void linehaulTripCopiesStopsFromTheRoute() throws Exception {
        createLinehaul().andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code", startsWith("VT-")))
                .andExpect(jsonPath("$.data.status").value("PLANNED"))
                .andExpect(jsonPath("$.data.routeCode").value("HCM-DN"))
                .andExpect(jsonPath("$.data.vehicleSeats").value(6))
                .andExpect(jsonPath("$.data.startLocationId").value(hcm))
                .andExpect(jsonPath("$.data.endLocationId").value(danang))
                .andExpect(jsonPath("$.data.plannedStartAt").value("2031-03-03T06:00:00Z"))
                .andExpect(jsonPath("$.data.plannedEndAt").value("2031-03-04T11:41:00Z"))
                .andExpect(jsonPath("$.data.stops.length()").value(8))
                .andExpect(jsonPath("$.data.stops[1].stopType").value("REST"))
                .andExpect(jsonPath("$.data.stops[1].plannedArrivalAt").value("2031-03-03T08:57:00Z"))
                .andExpect(jsonPath("$.data.stops[1].plannedDepartureAt").value("2031-03-03T09:27:00Z"))
                .andExpect(jsonPath("$.data.stops[7].locationId").value(danang));

        createTrip("""
                {"tripType":"LINEHAUL","vehicleId":%d}""".formatted(vehicle.getId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.departureId").exists());
    }

    @Test
    void linehaulNeedsTheManagerToConfirmTheRun() throws Exception {
        createTrip("""
                {"tripType":"LINEHAUL","departureId":%d,"vehicleId":%d}""".formatted(departure.getId(), vehicle.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chuyến chưa được quản lý xác nhận chạy, chưa lập lượt trục được."));
        createLinehaul().andExpect(status().isCreated());
    }

    @Test
    void vehicleCannotRunTwoOverlappingTripsOrStartAwayFromWhereItIs() throws Exception {
        createLinehaul().andExpect(status().isCreated());

        // Trùng giờ với lượt trục
        createTrip(localTrip(hcm, hcm, "2031-03-03T08:00:00Z", "2031-03-03T12:00:00Z"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Xe đã có lượt chạy khác trùng giờ."));
        // Sau lượt trục xe đang ở Đà Nẵng, không bắt đầu ở HCM được
        createTrip(localTrip(hcm, hcm, "2031-03-04T13:00:00Z", "2031-03-04T17:00:00Z"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", startsWith("Xe không ở điểm xuất phát")));
        // Trước lượt trục, xe kết thúc ở Đà Nẵng thì lượt trục (bắt đầu ở HCM) bị đứt
        createTrip(localTrip(hcm, danang, "2031-03-02T06:00:00Z", "2031-03-02T09:00:00Z"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", startsWith("Lượt sau của xe")));
        createTrip(localTrip(danang, danang, "2031-03-04T13:00:00Z", "2031-03-04T17:00:00Z"))
                .andExpect(status().isCreated());
    }

    @Test
    void onlyActiveVehicleIsAssigned() throws Exception {
        vehicle.setStatus(VehicleStatus.MAINTENANCE);
        vehicles.saveAndFlush(vehicle);
        createLinehaul().andExpect(status().isConflict()).andExpect(jsonPath("$.message", startsWith("Xe đang MAINTENANCE")));
    }

    @Test
    void localTripStopsAreCheckedAgainstTheBooking() throws Exception {
        Booking confirmed = testData.booking(testData.customer(testData.unique("dispatch.customer")), BookingType.LOCAL, BookingStatus.CONFIRMED);
        long pickup = testData.bookingAddress(confirmed, AddressRole.PICKUP).getId();
        Booking quoting = testData.booking(testData.customer(testData.unique("dispatch.customer")), BookingType.LOCAL, BookingStatus.QUOTING);
        long notReady = testData.bookingAddress(quoting, AddressRole.PICKUP).getId();

        // Điểm đầu là địa chỉ khách
        createTrip(trip("PICKUP", stopAddress(pickup, "PICKUP", "2031-03-10T06:00:00Z"), stopLocation(hcm, "HUB", "2031-03-10T09:00:00Z")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.stops").exists());
        // Địa chỉ đón dùng sai loại điểm dừng
        createTrip(trip("PICKUP", stopLocation(hcm, "HUB", "2031-03-10T06:00:00Z"), stopAddress(pickup, "DELIVERY", "2031-03-10T07:00:00Z"),
                stopLocation(hcm, "HUB", "2031-03-10T09:00:00Z")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", startsWith("Điểm dừng 2 là địa chỉ PICKUP")));
        // Đơn chưa cọc
        createTrip(trip("PICKUP", stopLocation(hcm, "HUB", "2031-03-10T06:00:00Z"), stopAddress(notReady, "PICKUP", "2031-03-10T07:00:00Z"),
                stopLocation(hcm, "HUB", "2031-03-10T09:00:00Z")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", startsWith("Đơn " + quoting.getCode())));

        createTrip(trip("PICKUP", stopLocation(hcm, "HUB", "2031-03-10T06:00:00Z"), stopAddress(pickup, "PICKUP", "2031-03-10T07:00:00Z"),
                stopLocation(hcm, "HUB", "2031-03-10T09:00:00Z")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stops[1].bookingCode").value(confirmed.getCode()))
                .andExpect(jsonPath("$.data.stops[1].stopType").value("PICKUP"))
                .andExpect(jsonPath("$.data.plannedEndAt").value("2031-03-10T09:00:00Z"));
    }

    @ParameterizedTest
    @CsvSource({"REST-NHATRANG, REST", "REST-NHATRANG, HUB", "HUB-HCM, REST"})
    void deliveryTripRequiresAHubStopAtAHubLocation(String locationCode, String stopType) throws Exception {
        long locationId = locations.findByCode(locationCode).orElseThrow().getId();
        createTrip(deliveryTripEndingAt(locationId, stopType))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.stops").value("Lượt đón, giao, chạy thẳng phải kết thúc ở hub để ngựa giao hụt có chỗ về."));
    }

    @Test
    void deliveryTripCanEndAtAHub() throws Exception {
        createTrip(deliveryTripEndingAt(hcm, "HUB"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.endLocationId").value(hcm))
                .andExpect(jsonPath("$.data.stops[2].stopType").value("HUB"))
                .andExpect(jsonPath("$.data.stops[2].locationId").value(hcm));
    }

    @Test
    void crewIsAssignedAcknowledgesAndCanBeReleased() throws Exception {
        long tripId = tripId(createLinehaul().andExpect(status().isCreated()));
        Staff driver = testData.staff(testData.unique("dispatch.driver"), "DRIVER");
        Staff escort = testData.staff(testData.unique("dispatch.escort"), "ESCORT");

        assign(tripId, driver, "DRIVER").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.crew[0].staffId").value(driver.getUserId()))
                .andExpect(jsonPath("$.data.crew[0].status").value("ASSIGNED"));
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_recipient WHERE user_id = ?", Integer.class, driver.getUserId())).isEqualTo(1);
        assign(tripId, driver, "DRIVER").andExpect(status().isConflict());
        assign(tripId, escort, "DRIVER").andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Nhân viên này không phải tài xế."));
        assign(tripId, escort, "ESCORT").andExpect(status().isCreated()).andExpect(jsonPath("$.data.crew.length()").value(2));

        String driverToken = bearer(driver, "DRIVER");
        String mine = mvc.perform(get("/crew-assignments/mine").header(HttpHeaders.AUTHORIZATION, driverToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].vehicleTripId").value(tripId))
                .andReturn().getResponse().getContentAsString();
        long assignmentId = ((Number) JsonPath.read(mine, "$.data[0].id")).longValue();

        mvc.perform(post("/crew-assignments/{id}/acknowledge", assignmentId).header(HttpHeaders.AUTHORIZATION, bearer(escort, "ESCORT")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/crew-assignments/{id}/acknowledge", assignmentId).header(HttpHeaders.AUTHORIZATION, driverToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"));

        mvc.perform(post("/crew-assignments/{id}/release", assignmentId).header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.crew[0].status").value("RELEASED"));
        mvc.perform(post("/crew-assignments/{id}/acknowledge", assignmentId).header(HttpHeaders.AUTHORIZATION, driverToken))
                .andExpect(status().isConflict());
        mvc.perform(get("/crew-assignments/mine").header(HttpHeaders.AUTHORIZATION, driverToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void staffCannotBeOnTwoOverlappingTripsOrStartAwayFromWhereTheyAre() throws Exception {
        long linehaul = tripId(createLinehaul());
        Staff driver = testData.staff(testData.unique("dispatch.driver"), "DRIVER");
        assign(linehaul, driver, "DRIVER").andExpect(status().isCreated());

        Vehicle other = testData.vehicle(2, 0);
        long overlapping = tripId(createTrip(localTripWith(other, hcm, hcm, "2031-03-03T08:00:00Z", "2031-03-03T12:00:00Z")));
        assign(overlapping, driver, "DRIVER").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Nhân viên đã có lượt chạy khác trùng giờ."));
        // Sau lượt trục tài xế đang ở Đà Nẵng
        long awayFromDriver = tripId(createTrip(localTripWith(other, hcm, hcm, "2031-03-05T06:00:00Z", "2031-03-05T09:00:00Z")));
        assign(awayFromDriver, driver, "DRIVER").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Nhân viên không ở điểm xuất phát")));
    }

    @Test
    void coordinatorCancelsATripThatHasNotStarted() throws Exception {
        long tripId = tripId(createLinehaul());
        Staff driver = testData.staff(testData.unique("dispatch.driver"), "DRIVER");
        assign(tripId, driver, "DRIVER").andExpect(status().isCreated());

        cancel(tripId, "{\"reason\":\" \"}").andExpect(status().isBadRequest());
        cancel(tripId, "{\"reason\":\"Đổi sang xe khác\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.crew[0].status").value("RELEASED"));
        cancel(tripId, "{\"reason\":\"Lần hai\"}").andExpect(status().isConflict());
        // Lượt đã hủy không còn giữ xe
        createLinehaul().andExpect(status().isCreated());
    }

    @Test
    void tripsAreListedByTimeWindowAndRolesAreEnforced() throws Exception {
        long tripId = tripId(createLinehaul());
        String manager = bearer(testData.staff(testData.unique("dispatch.manager"), "MANAGER"), "MANAGER");
        mvc.perform(get("/vehicle-trips").param("from", "2031-03-03T00:00:00Z").param("to", "2031-03-04T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.id == " + tripId + ")]").isNotEmpty());
        mvc.perform(get("/vehicle-trips").param("from", "2031-03-05T00:00:00Z").param("to", "2031-03-06T00:00:00Z")
                        .header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.id == " + tripId + ")]").isEmpty());

        Staff driver = testData.staff(testData.unique("dispatch.driver"), "DRIVER");
        mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, bearer(driver, "DRIVER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tripType\":\"LINEHAUL\",\"vehicleId\":1,\"departureId\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/crew-assignments/mine").header(HttpHeaders.AUTHORIZATION, coordinator)).andExpect(status().isForbidden());
        mvc.perform(get("/dispatch/crew-candidates").param("role", "DRIVER").header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.staffId == " + driver.getUserId() + ")]").isNotEmpty());
        mvc.perform(get("/dispatch/vehicles").header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.id == " + vehicle.getId() + ")]").isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.id == " + vehicle.getId() + ")].currentLocationName").isNotEmpty());
    }

    private ResultActions createLinehaul() throws Exception {
        confirmRun(departure.getId());
        return createTrip("""
                {"tripType":"LINEHAUL","departureId":%d,"vehicleId":%d}""".formatted(departure.getId(), vehicle.getId()));
    }

    private ResultActions createTrip(String body) throws Exception {
        return mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions assign(long tripId, Staff staff, String role) throws Exception {
        return mvc.perform(post("/vehicle-trips/{id}/crew-assignments", tripId).header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"staffId\":%d,\"crewRole\":\"%s\"}".formatted(staff.getUserId(), role)));
    }

    private ResultActions cancel(long tripId, String body) throws Exception {
        return mvc.perform(post("/vehicle-trips/{id}/cancel", tripId).header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String localTrip(long from, long to, String start, String end) {
        return localTripWith(vehicle, from, to, start, end);
    }

    private static String localTripWith(Vehicle v, long from, long to, String start, String end) {
        return """
                {"tripType":"DIRECT","vehicleId":%d,"stops":[%s,%s]}""".formatted(v.getId(), stopLocation(from, "HUB", start), stopLocation(to, "HUB", end));
    }

    private String deliveryTripEndingAt(long locationId, String stopType) {
        Booking booking = testData.booking(testData.customer(testData.unique("dispatch.customer")), BookingType.INTERPROVINCE, BookingStatus.IN_OPERATION);
        long delivery = testData.bookingAddress(booking, AddressRole.DELIVERY).getId();
        return trip("DELIVERY", stopLocation(hcm, "HUB", "2031-03-10T06:00:00Z"),
                stopAddress(delivery, "DELIVERY", "2031-03-10T07:00:00Z"), stopLocation(locationId, stopType, "2031-03-10T09:00:00Z"));
    }

    private String trip(String type, String... stops) {
        return """
                {"tripType":"%s","vehicleId":%d,"stops":[%s]}""".formatted(type, vehicle.getId(), String.join(",", stops));
    }

    private static String stopLocation(long locationId, String type, String at) {
        return """
                {"locationId":%d,"stopType":"%s","plannedArrivalAt":"%s","plannedDepartureAt":"%s"}""".formatted(locationId, type, at, at);
    }

    private static String stopAddress(long addressId, String type, String at) {
        return """
                {"bookingAddressId":%d,"stopType":"%s","plannedArrivalAt":"%s","plannedDepartureAt":"%s"}""".formatted(addressId, type, at, at);
    }

    private static long tripId(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private String bearer(Staff staff, String role) {
        return "Bearer " + jwtUtils.generateToken(staff.getUser().getUsername(), List.of(role));
    }

    // Quản lý đã xác nhận chạy chuyến (A6b): điều kiện để lập lượt trục
    private void confirmRun(long departureId) {
        entityManager.flush();
        jdbc.update("UPDATE departure SET run_confirmed_at = now(), run_confirmed_by = (SELECT min(id) FROM app_user) WHERE id = ? AND run_confirmed_at IS NULL", departureId);
        entityManager.clear();
    }
}
