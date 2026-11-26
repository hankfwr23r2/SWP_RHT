package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.entity.Staff;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

final class BenXeJourneyOperations {
    record Horse(long id, String chip) {}
    record Stop(long id, String type, Instant arrival, Instant departure) {}
    record Crew(String driver, String escort, long driverId, long escortId) {}

    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final AtomicReference<Instant> now;
    private final String coordinator;

    BenXeJourneyOperations(MockMvc mvc, JdbcTemplate jdbc, AtomicReference<Instant> now, String coordinator) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.now = now;
        this.coordinator = coordinator;
    }

    long planBranch(String type, long vehicle, long hub, long address, Instant start, Long rest, List<Horse> horses)
            throws Exception {
        Instant atAddress = start.plus(rest == null ? 60 : 180, ChronoUnit.MINUTES);
        var plannedStops = new ArrayList<String>();
        plannedStops.add(stop("locationId", hub, "HUB", start));
        if (rest != null) {
            plannedStops.add("""
                    {"locationId":%d,"stopType":"REST","plannedArrivalAt":"%s","plannedDepartureAt":"%s"}
                    """.formatted(rest, start.plus(120, ChronoUnit.MINUTES), start.plus(150, ChronoUnit.MINUTES)));
        }
        plannedStops.add(stop("bookingAddressId", address, type, atAddress));
        plannedStops.add(stop("locationId", hub, "HUB", atAddress.plus(90, ChronoUnit.MINUTES)));
        String body = """
                {"tripType":"%s","vehicleId":%d,"stops":[%s]}
                """.formatted(type, vehicle, String.join(",", plannedStops));
        long trip = number(ok(act(coordinator, "/vehicle-trips", body)), "$.data.id");
        var stops = stops(trip);
        boolean pickup = type.equals("PICKUP");
        for (Horse horse : horses) {
            ok(act(coordinator, "/vehicle-trips/" + trip + "/horse-legs", """
                    {"bookingHorseId":%d,"boardTripStopId":%d,"alightTripStopId":%d}
                    """.formatted(horse.id(), stops.get(pickup ? 1 : 0).id(), stops.get(pickup ? stops.size() - 1 : stops.size() - 2).id())));
        }
        return trip;
    }

    long allocateLinehaul(long departure, List<Horse> horses, boolean privateTruck) throws Exception {
        String path = "/departures/" + departure + "/linehaul";
        String suggestion = read(coordinator, path + "-suggestion");
        assertThat(number(suggestion, "$.data.missingSeats")).isZero();
        List<Map<String, Object>> trucks = JsonPath.read(suggestion, "$.data.trucks");
        var choices = new ArrayList<Map<String, Object>>();
        var assignedVehicles = new ArrayList<Long>();
        var previouslyUsed = jdbc.queryForList("SELECT DISTINCT vehicle_id FROM vehicle_trip", Long.class);
        int targetIndex = -1;
        var expected = horses.stream().map(Horse::id).toList();
        for (var truck : trucks) {
            List<Number> assigned = JsonPath.read(truck, "$.horses[*].bookingHorseId");
            if (assigned.isEmpty()) {
                continue;
            }
            List<Number> candidates = JsonPath.read(truck, "$.vehicles[*].id");
            long vehicle = candidates.stream().map(Number::longValue)
                    .filter(id -> !assignedVehicles.contains(id))
                    .min(java.util.Comparator.comparing(previouslyUsed::contains)).orElseThrow();
            List<Long> assignedIds = assigned.stream().map(Number::longValue).toList();
            if (assignedIds.stream().anyMatch(expected::contains)) {
                assertThat(assignedIds).containsExactlyInAnyOrderElementsOf(expected);
                assertThat(truck.get("allotmentType")).isEqualTo(privateTruck ? "PRIVATE" : "BASE");
                targetIndex = choices.size();
            }
            assignedVehicles.add(vehicle);
            choices.add(Map.of("allotmentId", truck.get("allotmentId"), "number", truck.get("number"),
                    "vehicleId", vehicle, "bookingHorseIds", assignedIds));
        }
        assertThat(targetIndex).isGreaterThanOrEqualTo(0);
        List<Number> trips = JsonPath.read(ok(act(coordinator, path + "-trips",
                new ObjectMapper().writeValueAsString(Map.of("trucks", choices)))), "$.data");
        assertThat(trips).hasSize(choices.size());
        long trip = trips.get(targetIndex).longValue();
        assertThat(jdbc.queryForObject("SELECT vehicle_id FROM vehicle_trip WHERE id = ?", Long.class, trip))
                .isEqualTo(assignedVehicles.get(targetIndex));
        assertThat(jdbc.queryForList("SELECT booking_horse_id FROM horse_leg WHERE vehicle_trip_id = ?", Long.class, trip))
                .containsExactlyInAnyOrderElementsOf(expected);
        return trip;
    }

    void assignCrew(long trip, Staff driver, Staff escort) throws Exception {
        assignCrew(trip, driver.getUserId(), "DRIVER");
        assignCrew(trip, escort.getUserId(), "ESCORT");
    }

    private void assignCrew(long trip, long member, String role) throws Exception {
        ok(act(coordinator, "/vehicle-trips/" + trip + "/crew-assignments", """
                {"staffId":%d,"crewRole":"%s"}
                """.formatted(member, role)));
    }

    void start(long trip, Crew crew) throws Exception {
        start(trip, crew.driver(), crew.escort());
    }

    private void start(long trip, String driver, String... escorts) throws Exception {
        var actors = new ArrayList<String>();
        actors.add(driver);
        actors.addAll(List.of(escorts));
        for (String actor : actors) {
            List<Number> assignments = JsonPath.read(read(actor, "/crew-assignments/mine"),
                    "$.data[?(@.vehicleTripId == %d)].id".formatted(trip));
            assertThat(assignments).hasSize(1);
            ok(act(actor, "/crew-assignments/" + assignments.getFirst() + "/acknowledge", "{}"));
        }
        now.set(stops(trip).getFirst().arrival());
        ok(act(driver, "/vehicle-trips/" + trip + "/start", "{}"));
    }

    void arrive(Crew crew, Stop stop) throws Exception {
        now.set(stop.arrival());
        ok(act(crew.driver(), "/trip-stops/" + stop.id() + "/arrive", "{}"));
    }

    ResultActions departure(Crew crew, Stop stop) throws Exception {
        now.set(stop.departure());
        return act(crew.driver(), "/trip-stops/" + stop.id() + "/depart", "{}");
    }

    void depart(Crew crew, Stop stop) throws Exception {
        ok(departure(crew, stop));
    }

    void rest(Crew crew, Stop stop, List<Horse> horses) throws Exception {
        for (Horse horse : horses) {
            ok(act(crew.escort(), "/health-logs", """
                    {"bookingHorseId":%d,"tripStopId":%d,"healthStatus":"NORMAL",
                     "waterIntake":"NORMAL","feedIntake":"NORMAL"}
                    """.formatted(horse.id(), stop.id())));
        }
    }

    void check(long trip, Stop stop, Crew crew, List<Horse> horses, String checkType) throws Exception {
        now.set(stop.arrival().minusSeconds(900));
        for (Horse horse : horses) {
            long stay = jdbc.queryForObject("""
                    SELECT hs.id FROM horse_stay hs JOIN trip_stop s ON s.location_id = hs.location_id
                    WHERE hs.booking_horse_id = ? AND s.id = ? AND hs.actual_start_at IS NOT NULL AND hs.actual_end_at IS NULL
                    """, Long.class, horse.id(), stop.id());
            ok(act(coordinator, "/health-check-tasks", """
                    {"bookingHorseId":%d,"vehicleTripId":%d,"assignedTo":%d,"checkType":"%s","dueAt":"%s"}
                    """.formatted(horse.id(), trip, crew.escortId(), checkType, stop.arrival())));
            ok(act(crew.escort(), "/health-logs", """
                    {"bookingHorseId":%d,"horseStayId":%d,"healthStatus":"NORMAL"}
                    """.formatted(horse.id(), stay)));
        }
    }

    void handover(long trip, Stop stop, Crew crew, List<Horse> horses, String type, String completion)
            throws Exception {
        List<Number> handovers = JsonPath.read(ok(act(crew.escort(), "/trip-stops/" + stop.id() + "/handovers",
                "{\"handoverType\":\"%s\"}".formatted(type))), "$.data.handovers[*].id");
        long handover = handovers.getLast().longValue();
        String path = "/handovers/" + handover;
        for (Horse horse : horses) {
            long leg = jdbc.queryForObject("""
                    SELECT id FROM horse_leg WHERE booking_horse_id = ? AND vehicle_trip_id = ?
                    """, Long.class, horse.id(), trip);
            ok(act(crew.escort(), path + "/horses", """
                    {"horseLegId":%d,"conditionNote":"Khỏe","microchip":"%s"}
                    """.formatted(leg, horse.chip())));
            upload(crew.escort(), path + "/horses/" + leg + "/photos");
        }
        upload(crew.escort(), path + "/record");
        if (type.equals("PICKUP") || type.equals("DELIVERY")) {
            List<Number> papers = JsonPath.read(read(crew.escort(), path + "/papers"), "$.data[*].documentId");
            for (Number document : papers) {
                ok(act(crew.escort(), path + "/papers", """
                        {"documentId":%d,"originalMissing":false}
                        """.formatted(document.longValue())));
            }
        }
        ok(act(crew.escort(), path + "/complete", completion));
    }

    long appoint(String specialist, long horse, Instant appointment) throws Exception {
        return number(ok(act(specialist, "/booking-horses/" + horse + "/hub-appointments",
                "{\"plannedStartAt\":\"%s\"}".formatted(appointment))), "$.data.id");
    }

    ResultActions selfDropOff(String staff, long stay, String chip, Instant appointment) throws Exception {
        now.set(appointment);
        String path = "/horse-stays/" + stay;
        List<Number> papers = JsonPath.read(read(staff, path + "/handover-papers?receiving=true"), "$.data[*].documentId");
        var body = new LinkedHashMap<String, Object>();
        body.put("microchip", chip);
        body.put("conditionNote", "Khỏe");
        body.put("papers", papers.stream().map(id -> Map.of("documentId", id, "originalMissing", false)).toList());
        return mvc.perform(multipart(path + "/receipts").file(image("photo")).file(image("record"))
                .file(new MockMultipartFile("request", "", "application/json",
                        new ObjectMapper().writeValueAsBytes(body))).header(HttpHeaders.AUTHORIZATION, staff));
    }

    void completeAtFinalHub(long trip, Crew crew) throws Exception {
        upload(crew.driver(), "/vehicle-trips/" + trip + "/return-photo");
        ok(act(crew.driver(), "/vehicle-trips/" + trip + "/complete", "{\"endVehicleCondition\":\"NORMAL\"}"));
    }

    void useDatabaseDeliveryTime(long booking) {
        // PostgreSQL đặt changed_at bằng now(), độc lập với Clock của ứng dụng trong test.
        now.set(jdbc.queryForObject("""
                SELECT changed_at FROM booking_status_history WHERE booking_id = ? AND to_status = 'DELIVERED'
                """, java.sql.Timestamp.class, booking).toInstant().plusSeconds(1));
    }

    void returnToHome(long linehaul, Crew crew, Instant start) throws Exception {
        var trip = jdbc.queryForMap("""
                SELECT vehicle_id, start_location_id, end_location_id FROM vehicle_trip WHERE id = ?
                """, linehaul);
        String body = """
                {"tripType":"TRANSFER","vehicleId":%s,"places":[{"locationId":%s},{"locationId":%s}],
                 "bookingHorseIds":[],"startAt":"%s"}
                """.formatted(trip.get("vehicle_id"), trip.get("end_location_id"), trip.get("start_location_id"), start);
        long returned = number(ok(act(coordinator, "/vehicle-trips/planned", body)), "$.data.id");
        assignCrew(returned, crew.driverId(), "DRIVER");
        start(returned, crew.driver());
        List<Stop> stops = stops(returned);
        for (Stop stop : stops) {
            arrive(crew, stop);
            if (!stop.equals(stops.getLast())) {
                depart(crew, stop);
            }
        }
        completeAtFinalHub(returned, crew);
        assertThat(jdbc.queryForObject("""
                SELECT t.status FROM vehicle_trip t JOIN vehicle v ON v.id = t.vehicle_id
                WHERE t.id = ? AND t.end_location_id = v.home_hub_location_id
                """, String.class, returned)).isEqualTo("COMPLETED");
    }

    void assertStay(long booking, String hub, String type, int horses) {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM horse_stay s JOIN booking_horse h ON h.id = s.booking_horse_id
                JOIN location l ON l.id = s.location_id
                WHERE h.booking_id = ? AND l.code = ? AND s.stay_type = ?
                    AND s.actual_start_at IS NOT NULL AND s.actual_end_at IS NULL
                """, Integer.class, booking, hub, type)).isEqualTo(horses);
    }

    void assertExpiredBorderDocuments(Crew crew, Stop stop) throws Exception {
        // Mô phỏng xe bị giữ tới ngày hôm sau khi giấy riêng đã hết hạn.
        // Request lỗi không ghi giờ rời. Sau kiểm tra, lượt chạy tiếp theo lịch gốc với hồ sơ còn hạn.
        now.set(stop.departure().plus(1, ChronoUnit.DAYS));
        act(crew.driver(), "/trip-stops/" + stop.id() + "/depart", "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.tripStopId", org.hamcrest.Matchers.containsString("thiếu")));
        assertThat(jdbc.queryForObject("SELECT actual_departure_at FROM trip_stop WHERE id = ?",
                java.sql.Timestamp.class, stop.id())).isNull();
    }

    void assertBorder(long trip, String gate) {
        assertThat(jdbc.queryForList("""
                SELECT l.code FROM trip_stop s JOIN location l ON l.id = s.location_id
                WHERE s.vehicle_trip_id = ? AND s.stop_type = 'BORDER' AND s.actual_departure_at IS NOT NULL
                """, String.class, trip)).containsExactly(gate);
    }

    void assertPickupFleet(long trip, long origin) {
        assertThat(jdbc.queryForObject("""
                SELECT v.home_hub_location_id FROM vehicle_trip t JOIN vehicle v ON v.id = t.vehicle_id WHERE t.id = ?
                """, Long.class, trip)).isEqualTo(origin);
        assertThat(jdbc.queryForList("""
                SELECT s.home_hub_location_id FROM crew_assignment c JOIN staff s ON s.user_id = c.staff_id
                WHERE c.vehicle_trip_id = ? AND c.crew_role IN ('DRIVER', 'ESCORT')
                """, Long.class, trip)).containsExactly(origin, origin);
    }

    void assertCompletion(long booking, List<Horse> horses, boolean selfPickup, String origin, String destination) {
        assertThat(jdbc.queryForList("""
                SELECT to_status FROM booking_status_history WHERE booking_id = ? ORDER BY id
                """, String.class, booking)).containsExactly("PENDING_INTAKE", "UNDER_REVIEW", "QUOTING",
                "AWAITING_DEPOSIT", "CONFIRMED", "IN_OPERATION", "DELIVERED", "COMPLETED");
        assertThat(jdbc.queryForList("""
                SELECT l.leg_type FROM horse_leg l JOIN booking_horse h ON h.id = l.booking_horse_id
                WHERE h.booking_id = ? AND l.status = 'COMPLETED' ORDER BY h.id, l.sequence_number
                """, String.class, booking)).containsExactlyElementsOf(horses.stream().flatMap(horse ->
                (selfPickup ? List.of("LINEHAUL", "DELIVERY") : List.of("PICKUP", "LINEHAUL", "DELIVERY"))
                        .stream()).toList());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM payment WHERE booking_id = ? AND payment_type = 'BALANCE' AND status = 'PAID'
                """, Integer.class, booking)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM horse_stay s JOIN booking_horse h ON h.id = s.booking_horse_id
                WHERE h.booking_id = ? AND s.actual_start_at IS NOT NULL AND s.actual_end_at IS NULL
                """, Integer.class, booking)).isZero();
        for (Horse horse : horses) {
            var expected = new ArrayList<String>();
            expected.add("PICKUP:" + (selfPickup ? origin : ""));
            if (!selfPickup) {
                expected.add("HUB_IN:" + origin);
            }
            expected.addAll(List.of("HUB_OUT:" + origin, "HUB_IN:" + destination, "HUB_OUT:" + destination, "DELIVERY:"));
            assertThat(jdbc.queryForList("""
                    SELECT h.handover_type || ':' || coalesce(l.code, '') FROM handover h
                    JOIN handover_horse hh ON hh.handover_id = h.id
                    JOIN horse_leg leg ON leg.id = hh.horse_leg_id
                    LEFT JOIN trip_stop p ON p.id = h.trip_stop_id
                    LEFT JOIN horse_stay s ON s.id = h.horse_stay_id
                    LEFT JOIN location l ON l.id = coalesce(p.location_id, s.location_id)
                    WHERE leg.booking_horse_id = ? AND h.status = 'DONE' ORDER BY h.id
                    """, String.class, horse.id())).containsExactlyElementsOf(expected);
        }
    }

    List<Stop> stops(long trip) {
        return jdbc.query("""
                SELECT id, stop_type, planned_arrival_at, planned_departure_at FROM trip_stop
                WHERE vehicle_trip_id = ? ORDER BY sequence_number
                """, (row, i) -> new Stop(row.getLong(1), row.getString(2),
                row.getTimestamp(3).toInstant(), row.getTimestamp(4).toInstant()), trip);
    }

    private void upload(String actor, String path) throws Exception {
        ok(mvc.perform(multipart(path).file(image("file")).header(HttpHeaders.AUTHORIZATION, actor)));
    }

    private static MockMultipartFile image(String part) {
        return new MockMultipartFile(part, "proof.jpg", "image/jpeg", new byte[] {(byte) 255, (byte) 216, (byte) 255});
    }

    private ResultActions act(String actor, String path, String body) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, actor)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String read(String actor, String path) throws Exception {
        return ok(mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, actor)));
    }

    static String ok(ResultActions action) throws Exception {
        var result = action.andReturn();
        var response = result.getResponse();
        assertThat(response.getStatus()).as("%s %s: %s", result.getRequest().getMethod(),
                result.getRequest().getRequestURI(), response.getContentAsString()).isBetween(200, 299);
        return response.getContentAsString();
    }

    static long number(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private static String stop(String key, long id, String type, Instant at) {
        return """
                {"%s":%d,"stopType":"%s","plannedArrivalAt":"%s","plannedDepartureAt":"%s"}
                """.formatted(key, id, type, at, at);
    }
}