package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

// B1: gợi ý gom đơn vào lượt đón và lượt giao. Hub TP.HCM ở Long Trường; đơn liên tỉnh đón ở Long Thành (phía đông),
// đơn xuất đón ở CLB Sài Gòn (phía tây)
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class TripGroupingFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private LocationRepository locations;
    @Autowired private EntityManager entityManager;

    private String coordinator;
    private long domestic;
    private long export;
    private LocalDate pickupDate;
    private long hcm;

    @BeforeEach
    void setUp() {
        var staff = testData.staff(testData.unique("group.coordinator"), "COORDINATOR");
        coordinator = "Bearer " + jwtUtils.generateToken(staff.getUser().getUsername(), List.of("COORDINATOR"));
        domestic = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DOMESTIC-001'", Long.class);
        export = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-EXPORT-001'", Long.class);
        pickupDate = jdbc.queryForObject("SELECT requested_pickup_date FROM booking WHERE id = ?", java.sql.Date.class, domestic).toLocalDate();
        hcm = locations.findByCode("HUB-HCM").orElseThrow().getId();
        // Hai đơn cùng ngày đón, cùng hub đầu tuyến
        sql("UPDATE booking SET requested_pickup_date = '" + pickupDate + "' WHERE id = " + export);
    }

    @Test
    void bookingsInOppositeDirectionsAreSuggestedAsSeparatePickups() throws Exception {
        suggest(pickupDate, hcm, "PICKUP").andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void bookingsInTheSameDirectionShareOnePickupFarthestFirstAndReachTheHubThreeHoursEarly() throws Exception {
        // Dời điểm đón của đơn xuất sang phía đông, nằm giữa hub và Long Thành
        sql("UPDATE booking_address SET latitude = 10.790000, longitude = 106.890000 WHERE booking_id = " + export + " AND address_type = 'PICKUP'");
        String body = content(suggest(pickupDate, hcm, "PICKUP").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].bookings[0].bookingCode").value("DEV-DOMESTIC-001"))
                .andExpect(jsonPath("$.data[0].bookings[1].bookingCode").value("DEV-EXPORT-001"))
                .andExpect(jsonPath("$.data[0].stops.length()").value(4))
                .andExpect(jsonPath("$.data[0].stops[0].stopType").value("HUB"))
                .andExpect(jsonPath("$.data[0].stops[1].stopType").value("PICKUP"))
                .andExpect(jsonPath("$.data[0].stops[3].locationId").value(hcm)));
        Instant firstDeparture = jdbc.queryForObject("""
                SELECT min(d.planned_departure_at) FROM booking b JOIN departure d ON d.id = b.departure_id WHERE b.id IN (?, ?)""",
                java.sql.Timestamp.class, domestic, export).toInstant();
        assertThat(Instant.parse(JsonPath.read(body, "$.data[0].stops[3].plannedArrivalAt"))).isEqualTo(firstDeparture.minusSeconds(3 * 3600));
        assertThat(((Number) JsonPath.read(body, "$.data[0].seats")).intValue())
                .isEqualTo(jdbc.queryForObject("SELECT count(*) FROM booking_horse WHERE booking_id IN (?, ?)", Integer.class, domestic, export));

        // Con đã có chặng đón thì không gợi ý nữa
        long trip = ((Number) JsonPath.read(content(mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON).content("{\"tripType\":\"PICKUP\",\"vehicleId\":%d,\"stops\":%s}".formatted(
                        testData.vehicle(9, 0).getId(), stopsJson(body)))).andExpect(status().isCreated())), "$.data.id")).longValue();
        List<Number> stopIds = JsonPath.read(content(mvc.perform(get("/vehicle-trips/{id}", trip).header(HttpHeaders.AUTHORIZATION, coordinator))), "$.data.stops[*].id");
        for (long line : jdbc.queryForList("SELECT id FROM booking_horse WHERE booking_id = ?", Long.class, export)) {
            mvc.perform(post("/vehicle-trips/{id}/horse-legs", trip).header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"bookingHorseId\":%d,\"boardTripStopId\":%d,\"alightTripStopId\":%d}".formatted(line, stopIds.get(2).longValue(), stopIds.get(3).longValue())))
                    .andExpect(status().isCreated());
        }
        suggest(pickupDate, hcm, "PICKUP").andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].bookings.length()").value(1))
                .andExpect(jsonPath("$.data[0].bookings[0].bookingCode").value("DEV-DOMESTIC-001"));
    }

    @Test
    void deliveriesStartAfterTheLinehaulReachesTheDestinationHub() throws Exception {
        long danang = locations.findByCode("HUB-DN").orElseThrow().getId();
        Instant arrival = jdbc.queryForObject("""
                SELECT d.planned_departure_at + make_interval(mins => r.estimated_duration_minutes)
                FROM booking b JOIN departure d ON d.id = b.departure_id JOIN route r ON r.id = d.route_id WHERE b.id = ?""",
                java.sql.Timestamp.class, domestic).toInstant();
        LocalDate day = LocalDate.ofInstant(arrival, ZoneId.of("Asia/Ho_Chi_Minh"));
        String body = content(suggest(day, danang, "DELIVERY").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].bookings[0].bookingCode").value("DEV-DOMESTIC-001"))
                .andExpect(jsonPath("$.data[0].stops[1].stopType").value("DELIVERY")));
        assertThat(Instant.parse(JsonPath.read(body, "$.data[0].stops[0].plannedDepartureAt"))).isEqualTo(arrival.plusSeconds(3600));
        suggest(day, danang, "LINEHAUL").andExpect(status().isBadRequest());
    }

    private ResultActions suggest(LocalDate date, long hubId, String type) throws Exception {
        return mvc.perform(get("/dispatch/grouping-suggestions").header(HttpHeaders.AUTHORIZATION, coordinator)
                .param("date", date.toString()).param("hubId", String.valueOf(hubId)).param("tripType", type));
    }

    // Điểm dừng gợi ý dùng thẳng để lập lượt xe
    private static String stopsJson(String body) {
        List<Object> stops = JsonPath.read(body, "$.data[0].stops");
        return com.jayway.jsonpath.JsonPath.parse(stops).jsonString();
    }

    private void sql(String statement) {
        entityManager.flush();
        jdbc.update(statement);
        entityManager.clear();
    }

    private static String content(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
