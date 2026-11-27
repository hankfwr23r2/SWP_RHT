package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.client.ValhallaClient;
import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.dto.route.RoadRouteDto;
import com.example.racehorse_transport.exception.RoutingUnavailableException;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Lượt đón của đơn mẫu DEV-DOMESTIC-001, giờ do hệ thống tính. Không chạy trong transaction của test vì gọi bản đồ nằm ngoài transaction
@PostgresIntegrationTest
@AutoConfigureMockMvc
class TripScheduleFlowTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwt;
    @Autowired private LocationRepository locations;
    @MockitoBean private ValhallaClient routing;

    private String coordinator;
    private long hub;
    private long address;
    private List<Long> horses;
    private LocalDate pickupDate;
    private Long vehicle;
    private Long createdTrip;

    @BeforeEach
    void setUp() {
        var staff = testData.staff(testData.unique("plan.coordinator"), "COORDINATOR");
        coordinator = "Bearer " + jwt.generateToken(staff.getUser().getUsername(), List.of("COORDINATOR"), 0);
        long booking = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DOMESTIC-001'", Long.class);
        hub = locations.findByCode("HUB-HCM").orElseThrow().getId();
        address = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'PICKUP'", Long.class, booking);
        horses = jdbc.queryForList("SELECT id FROM booking_horse WHERE booking_id = ? ORDER BY id", Long.class, booking);
        pickupDate = jdbc.queryForObject("SELECT requested_pickup_date FROM booking WHERE id = ?", java.sql.Date.class, booking).toLocalDate();
        vehicle = testData.vehicle(9, 0).getId();
        drive(3600);
    }

    @AfterEach
    void cleanUp() {
        if (createdTrip != null) {
            jdbc.update("DELETE FROM coordinator_assignment_history WHERE vehicle_trip_id = ?", createdTrip);
            jdbc.update("DELETE FROM horse_leg WHERE vehicle_trip_id = ?", createdTrip);
            jdbc.update("DELETE FROM trip_stop WHERE vehicle_trip_id = ?", createdTrip);
            jdbc.update("DELETE FROM vehicle_trip WHERE id = ?", createdTrip);
        }
    }

    @Test
    void previewArrivesAtTheCustomerWhenTheWindowOpensAndSavesNothing() throws Exception {
        int before = jdbc.queryForObject("SELECT count(*) FROM vehicle_trip", Integer.class);
        String body = content(plan("null").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(false))
                .andExpect(jsonPath("$.data.stops.length()").value(3))
                .andExpect(jsonPath("$.data.stops[1].stopType").value("PICKUP"))
                .andExpect(jsonPath("$.data.stops[1].horses").value(horses.size()))
                .andExpect(jsonPath("$.data.stops[1].path.length()").value(2)));
        Instant arrival = Instant.parse(JsonPath.read(body, "$.data.stops[1].arrivalAt"));
        assertThat(arrival).isEqualTo(pickupDate.atTime(6, 0).atZone(ZONE).toInstant());
        Instant leave = Instant.parse(JsonPath.read(body, "$.data.stops[1].departureAt"));
        assertThat(leave).isEqualTo(arrival.plusSeconds(60L * (20 + 10L * horses.size())));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vehicle_trip", Integer.class)).isEqualTo(before);
    }

    @Test
    void createSavesTheTripWithAllHorsesAtOnce() throws Exception {
        String body = content(create().andExpect(status().isCreated()));
        createdTrip = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horse_leg WHERE vehicle_trip_id = ? AND status = 'PLANNED'", Integer.class, createdTrip)).isEqualTo(horses.size());
        // Gán lại cùng các con: cả lần lập bị từ chối, không để lại lượt xe dở dang
        int before = jdbc.queryForObject("SELECT count(*) FROM vehicle_trip", Integer.class);
        vehicle = testData.vehicle(9, 0).getId();
        create().andExpect(status().is4xxClientError());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vehicle_trip", Integer.class)).isEqualTo(before);
    }

    @Test
    void ridesOverTheLimitAndLateArrivalAreBlocked() throws Exception {
        drive(9 * 3600);
        plan("null").andExpect(status().isConflict());
        create().andExpect(status().isConflict());
        drive(3600);
        Instant departure = jdbc.queryForObject("SELECT d.planned_departure_at FROM booking b JOIN departure d ON d.id = b.departure_id WHERE b.code = 'DEV-DOMESTIC-001'",
                java.sql.Timestamp.class).toInstant();
        plan("\"" + departure.minusSeconds(3 * 3600) + "\"").andExpect(jsonPath("$.data.warnings", org.hamcrest.Matchers.hasItem("LATE_FOR_HUB")));
    }

    @Test
    void sixHourPickupInsertsARealRestStopAndKeepsTheHorseAssignedToTheHub() throws Exception {
        when(routing.truckRoute(anyList(), anyDouble(), anyInt(), any())).thenAnswer(invocation -> {
            List<GeoPointDto> points = invocation.getArgument(0);
            boolean pickupLoop = points.getFirst().equals(points.getLast());
            long first = pickupLoop ? 3600 : 3 * 3600;
            long second = pickupLoop ? 6 * 3600 : 3 * 3600;
            return new RoadRouteDto(80, first + second, List.of(
                    new RoadRouteDto.Leg(40, first, List.of(points.get(0), points.get(1))),
                    new RoadRouteDto.Leg(40, second, List.of(points.get(1), points.get(2)))));
        });
        String body = content(create().andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stops[2].stopType").value("REST")));
        createdTrip = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM horse_leg hl JOIN trip_stop ts ON ts.id = hl.alight_trip_stop_id
                WHERE hl.vehicle_trip_id = ? AND ts.stop_type = 'HUB' AND ts.sequence_number = 4
                """, Integer.class, createdTrip)).isEqualTo(horses.size());
        assertThat(jdbc.queryForObject("""
                SELECT extract(epoch FROM planned_departure_at - planned_arrival_at)::int
                FROM trip_stop WHERE vehicle_trip_id = ? AND stop_type = 'REST'
                """, Integer.class, createdTrip)).isEqualTo(1800);
    }

    @Test
    void sixHourPickupWithNoReachableRestStopIsBlocked() throws Exception {
        drive(6 * 3600);
        create().andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.places", org.hamcrest.Matchers.containsString("Không có trạm nghỉ phù hợp")));
    }

    @Test
    void rejectsWrongInputAndReportsWhenTheMapServiceIsDown() throws Exception {
        mvc.perform(post("/dispatch/trip-plans").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripType\":\"PICKUP\",\"places\":[{\"locationId\":%d},{\"locationId\":%d}],\"bookingHorseIds\":%s}".formatted(hub, hub, horses)))
                .andExpect(status().isBadRequest());
        when(routing.truckRoute(anyList(), anyDouble(), anyInt(), any())).thenThrow(new RoutingUnavailableException());
        plan("null").andExpect(status().isServiceUnavailable());
        var manager = testData.staff(testData.unique("plan.manager"), "MANAGER");
        coordinator = "Bearer " + jwt.generateToken(manager.getUser().getUsername(), List.of("MANAGER"), 0);
        plan("null").andExpect(status().isForbidden());
    }

    @Test
    void previewsAnEmptyReturnByRoadWhenNoReverseRouteExists() throws Exception {
        long gate = locations.findByCode("GATE-LAOBAO").orElseThrow().getId();
        mvc.perform(post("/dispatch/trip-plans").header(HttpHeaders.AUTHORIZATION, coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"tripType":"TRANSFER","vehicleId":%d,"places":[{"locationId":%d},{"locationId":%d}],
                                "bookingHorseIds":[],"startAt":"2031-03-04T11:41:00Z"}
                                """.formatted(vehicle, gate, hub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(false))
                .andExpect(jsonPath("$.data.driveMinutes").value(60))
                .andExpect(jsonPath("$.data.stops.length()").value(2))
                .andExpect(jsonPath("$.data.stops[0].stopType").value("BORDER"))
                .andExpect(jsonPath("$.data.stops[1].locationId").value(hub))
                .andExpect(jsonPath("$.data.endAt").value("2031-03-04T12:41:00Z"));
    }

    private void drive(long seconds) {
        when(routing.truckRoute(anyList(), anyDouble(), anyInt(), any())).thenAnswer(invocation -> {
            List<GeoPointDto> points = invocation.getArgument(0);
            List<RoadRouteDto.Leg> legs = IntStream.range(1, points.size())
                    .mapToObj(i -> new RoadRouteDto.Leg(40, seconds, List.of(points.get(i - 1), points.get(i)))).toList();
            return new RoadRouteDto(40.0 * legs.size(), seconds * legs.size(), legs);
        });
    }

    private ResultActions plan(String startAt) throws Exception {
        return send("/dispatch/trip-plans", startAt);
    }

    private ResultActions create() throws Exception {
        return send("/vehicle-trips/planned", "null");
    }

    private ResultActions send(String path, String startAt) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripType\":\"PICKUP\",\"vehicleId\":%d,\"places\":[{\"locationId\":%d},{\"bookingAddressId\":%d},{\"locationId\":%d}],\"bookingHorseIds\":%s,\"startAt\":%s}"
                        .formatted(vehicle, hub, address, hub, horses, startAt)));
    }

    private static String content(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
