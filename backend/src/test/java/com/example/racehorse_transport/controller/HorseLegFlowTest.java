package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.example.racehorse_transport.service.HorseLegService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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

// B1: xếp ngựa của 3 đơn seed vào lượt xe (chạy thẳng, trục nội địa, quốc tế) và các quy tắc chặng
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class HorseLegFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HorseLegService horseLegs;
    @Autowired private EntityManager entityManager;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private LocationRepository locations;

    private String coordinator;
    private long hcm;

    @BeforeEach
    void setUp() {
        String username = testData.unique("legs.coordinator");
        testData.staff(username, "COORDINATOR");
        coordinator = "Bearer " + jwtUtils.generateToken(username, List.of("COORDINATOR"));
        hcm = locations.findByCode("HUB-HCM").orElseThrow().getId();
    }

    @Test
    void directBookingRidesOneTripFromPickupToDelivery() throws Exception {
        Seed seed = seed("DEV-DIRECT-001");
        Instant start = seed.pickupDate().atTime(1, 0).toInstant(java.time.ZoneOffset.UTC);
        long trip = tripId(createTrip(testData.vehicle(4, 0), "DIRECT", stop(hcm, "HUB", start, 0), address(seed.pickup(), "PICKUP", start, 60),
                address(seed.delivery(), "DELIVERY", start, 240), stop(hcm, "HUB", start, 480)));
        List<Long> stops = stopIds(trip);

        // Chạy thẳng phải xuống ở địa chỉ giao
        assign(trip, seed.horses().getFirst(), stops.get(1), stops.get(3)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chạy thẳng phải xuống xe ở địa chỉ giao của đơn."));
        assign(trip, seed.horses().getFirst(), stops.get(2), stops.get(1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Điểm xuống phải sau điểm lên."));
        assign(trip, seed.horses().getFirst(), stops.get(1), stops.get(2)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].bookingCode").value("DEV-DIRECT-001"))
                .andExpect(jsonPath("$.data[0].leg.legType").value("DIRECT"))
                .andExpect(jsonPath("$.data[0].leg.status").value("PLANNED"))
                .andExpect(jsonPath("$.data[0].leg.sequenceNumber").value(1));
        assign(trip, seed.horses().getFirst(), stops.get(1), stops.get(2)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ngựa đã có trên lượt xe này."));
    }

    @ParameterizedTest
    @CsvSource({"PICKUP, 8, 0", "PICKUP, 8, 1", "DIRECT, 10, 0", "DIRECT, 10, 1"})
    void plannedRideIncludesTimeAtIntermediateStops(String type, int limitHours, int minutesOver) throws Exception {
        boolean direct = "DIRECT".equals(type);
        Seed seed = seed(direct ? "DEV-DIRECT-001" : "DEV-DOMESTIC-001");
        Seed other = seed(direct ? "DEV-DOMESTIC-001" : "DEV-DIRECT-001");
        Instant start = direct ? seed.pickupDate().atTime(1, 0).toInstant(java.time.ZoneOffset.UTC)
                : seed.departureAt().minusSeconds(14 * 3600);
        int alightMinute = 90 + limitHours * 60 + minutesOver;
        // Quy chế đội xe Q2: chặng đón tối đa 8 giờ, chạy thẳng 10 giờ, ngồi xe quá 4 giờ phải có trạm nghỉ
        long rest = locations.findByCode("REST-PHANTHIET").orElseThrow().getId();
        var plan = new ArrayList<>(List.of(stop(hcm, "HUB", start, 0), address(seed.pickup(), "PICKUP", start, 60),
                rest(rest, start, 300), rest(rest, start, 420),
                address(other.pickup(), "PICKUP", start, alightMinute - 90),
                direct ? address(seed.delivery(), "DELIVERY", start, alightMinute) : stop(hcm, "HUB", start, alightMinute)));
        if (direct) {
            plan.add(stop(hcm, "HUB", start, alightMinute + 60));
        }
        long trip = tripId(createTrip(testData.vehicle(4, 0), type, plan.toArray(String[]::new)));
        List<Long> stops = stopIds(trip);

        var result = assign(trip, seed.horses().getFirst(), stops.get(1), stops.get(5));
        if (minutesOver == 0) {
            result.andExpect(status().isCreated());
        } else {
            result.andExpect(status().isConflict())
                    .andExpect(jsonPath("$.data.alightTripStopId")
                            .value("Ngựa ngồi xe quá " + limitHours + " giờ theo kế hoạch. Bỏ bớt điểm hoặc tách lượt."));
        }
    }

    @Test
    void domesticBookingGoesPickupLinehaulDeliveryAsOneChain() throws Exception {
        Seed seed = seed("DEV-DOMESTIC-001");
        long danang = locations.findByCode("HUB-DN").orElseThrow().getId();
        Instant pickupStart = seed.departureAt().minusSeconds(10 * 3600);
        long pickupTrip = tripId(createTrip(testData.vehicle(4, 0), "PICKUP", stop(hcm, "HUB", pickupStart, 0),
                address(seed.pickup(), "PICKUP", pickupStart, 90), stop(hcm, "HUB", pickupStart, 240)));
        Vehicle linehaulVehicle = testData.vehicle(4, 1);
        long linehaul = tripId(createTrip(linehaulVehicle, "LINEHAUL", seed.departureId()));

        // Chưa có chặng nào thì không đi thẳng vào lượt trục rồi giao bỏ qua đón: chặng giao đầu tiên không xuất phát ở địa chỉ đón
        Instant linehaulEnd = Instant.parse(JsonPath.read(trip(linehaul), "$.data.plannedEndAt"));
        long delivery = tripId(createTrip(linehaulVehicle, "DELIVERY", stop(danang, "HUB", linehaulEnd, 30),
                address(seed.delivery(), "DELIVERY", linehaulEnd, 120), stop(danang, "HUB", linehaulEnd, 300)));
        List<Long> deliveryStops = stopIds(delivery);
        assign(delivery, seed.horses().getFirst(), deliveryStops.get(0), deliveryStops.get(1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Chặng mới phải bắt đầu ở nơi chặng trước")));

        List<Long> pickupStops = stopIds(pickupTrip);
        for (long horse : seed.horses()) {
            assign(pickupTrip, horse, pickupStops.get(1), pickupStops.get(2)).andExpect(status().isCreated());
        }

        List<Long> linehaulStops = stopIds(linehaul);
        // Quy tắc #21: chỉ lên ở điểm đầu tuyến
        assign(linehaul, seed.horses().getFirst(), linehaulStops.get(1), linehaulStops.getLast()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Ngựa chỉ lên xe trục ở điểm đầu tuyến")));
        assign(linehaul, seed.horses().getFirst(), linehaulStops.getFirst(), linehaulStops.getLast()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].leg.legType").value("LINEHAUL"))
                .andExpect(jsonPath("$.data[0].leg.sequenceNumber").value(2));

        // Quy tắc #30: con thứ hai cùng đơn không đi lượt trục khác
        long otherLinehaul = tripId(createTrip(testData.vehicle(4, 0), "LINEHAUL", seed.departureId()));
        List<Long> otherStops = stopIds(otherLinehaul);
        assign(otherLinehaul, seed.horses().get(1), otherStops.getFirst(), otherStops.getLast()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ngựa cùng đơn phải đi cùng một lượt xe trục."));
        assign(linehaul, seed.horses().get(1), linehaulStops.getFirst(), linehaulStops.getLast()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.length()").value(2));

        assign(delivery, seed.horses().getFirst(), deliveryStops.get(0), deliveryStops.get(1)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].leg.legType").value("DELIVERY"))
                .andExpect(jsonPath("$.data[0].leg.sequenceNumber").value(3));

        // Bảng điều độ thấy đủ chuỗi 3 chặng của con đầu
        mvc.perform(get("/dispatch/bookings").param("from", seed.pickupDate().toString()).param("to", seed.pickupDate().toString())
                        .header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'DEV-DOMESTIC-001')].horses[0].legs.length()").value(3))
                .andExpect(jsonPath("$.data[?(@.code == 'DEV-DOMESTIC-001')].routeCode").value("HCM-DN"));

        // Gỡ chặng: không gỡ chặng đón khi còn chặng sau; gỡ chặng cuối được
        long pickupLeg = legId(seed.horses().getFirst(), 1);
        mvc.perform(post("/horse-legs/{id}/cancel", pickupLeg).header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", startsWith("Con ngựa còn chặng sau")));
        mvc.perform(post("/horse-legs/{id}/cancel", legId(seed.horses().getFirst(), 3)).header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        // Lượt có ngựa thì không hủy được
        mvc.perform(post("/vehicle-trips/{id}/cancel", linehaul).header(HttpHeaders.AUTHORIZATION, coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Thử\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void customerIsToldTheExpectedWindowOncePerTripAndAgainOnlyWhenItMovesPastTheThreshold() throws Exception {
        Seed seed = seed("DEV-DOMESTIC-001");
        Instant start = seed.departureAt().minusSeconds(10 * 3600);
        List<Long> first = pickup(seed, start, 60);
        List<Long> nearby = pickup(seed, start, 80);
        List<Long> later = pickup(seed, start, 180);

        for (long horse : seed.horses()) assignStops(first, horse);
        assertThat(notices(seed)).containsExactly("Giờ xe dự kiến");
        assertThat(lastMessage(seed)).contains("xe dự kiến đến đón ngựa").contains("–");

        // Gỡ khỏi lượt, gán lượt lệch 20 phút (ngưỡng 30): không báo lại; lệch 2 giờ: báo đổi giờ, cũng chỉ một lần
        moveAll(seed, nearby);
        assertThat(notices(seed)).containsExactly("Giờ xe dự kiến");
        moveAll(seed, later);
        assertThat(notices(seed)).containsExactly("Giờ xe dự kiến", "Giờ dự kiến thay đổi");
        // Hành trình khách thấy cùng khung giờ, chặng đã gỡ thì không còn khung giờ
        mvc.perform(get("/bookings/{id}/journey", seed.bookingId()).header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.horses[0].legs[0].pickupWindow").doesNotExist())
                .andExpect(jsonPath("$.data.horses[0].legs[2].pickupWindow.from").isNotEmpty())
                .andExpect(jsonPath("$.data.horses[0].legs[2].pickupWindow.to").isNotEmpty())
                .andExpect(jsonPath("$.data.horses[0].legs[2].deliveryWindow").doesNotExist());
    }

    // Lượt đón tới địa chỉ đón sau `minutes` phút; trả về mã lượt xe, điểm lên, điểm xuống
    private List<Long> pickup(Seed seed, Instant start, int minutes) throws Exception {
        long trip = tripId(createTrip(testData.vehicle(4, 0), "PICKUP", stop(hcm, "HUB", start, 0),
                address(seed.pickup(), "PICKUP", start, minutes), stop(hcm, "HUB", start, 300)));
        List<Long> stops = stopIds(trip);
        return List.of(trip, stops.get(1), stops.get(2));
    }

    private void assignStops(List<Long> trip, long horse) throws Exception {
        assign(trip.get(0), horse, trip.get(1), trip.get(2)).andExpect(status().isCreated());
    }

    private void moveAll(Seed seed, List<Long> trip) throws Exception {
        for (long horse : seed.horses()) {
            long planned = jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND status = 'PLANNED'", Long.class, horse);
            mvc.perform(post("/horse-legs/{id}/cancel", planned).header(HttpHeaders.AUTHORIZATION, coordinator)).andExpect(status().isOk());
        }
        for (long horse : seed.horses()) assignStops(trip, horse);
    }

    private List<String> notices(Seed seed) {
        entityManager.flush();
        return jdbc.queryForList("SELECT title FROM notification WHERE booking_id = ? AND title LIKE 'Giờ%' ORDER BY id", String.class, seed.bookingId());
    }

    private String lastMessage(Seed seed) {
        return jdbc.queryForObject("SELECT message FROM notification WHERE booking_id = ? AND title LIKE 'Giờ%' ORDER BY id DESC LIMIT 1", String.class, seed.bookingId());
    }

    @Test
    void bookingLeavingItsDepartureLosesAllPlannedLegs() throws Exception {
        Seed seed = seed("DEV-DOMESTIC-001");
        Instant pickupStart = seed.departureAt().minusSeconds(10 * 3600);
        long pickupTrip = tripId(createTrip(testData.vehicle(4, 0), "PICKUP", stop(hcm, "HUB", pickupStart, 0),
                address(seed.pickup(), "PICKUP", pickupStart, 90), stop(hcm, "HUB", pickupStart, 240)));
        long linehaul = tripId(createTrip(testData.vehicle(4, 0), "LINEHAUL", seed.departureId()));
        List<Long> pickupStops = stopIds(pickupTrip);
        List<Long> linehaulStops = stopIds(linehaul);
        for (long horse : seed.horses()) {
            assign(pickupTrip, horse, pickupStops.get(1), pickupStops.get(2)).andExpect(status().isCreated());
            assign(linehaul, horse, linehaulStops.getFirst(), linehaulStops.getLast()).andExpect(status().isCreated());
        }

        long appointment = jdbc.queryForObject("""
                INSERT INTO horse_stay (booking_horse_id, stay_type, location_id, planned_start_at, planned_end_at)
                VALUES (?, 'ASSEMBLY', ?, now(), now() + interval '1 hour') RETURNING id
                """, Long.class, seed.horses().getFirst(), hcm);
        long started = jdbc.queryForObject("""
                INSERT INTO horse_stay (booking_horse_id, stay_type, location_id, planned_start_at, planned_end_at, actual_start_at)
                VALUES (?, 'ASSEMBLY', ?, now(), now() + interval '1 hour', now()) RETURNING id
                """, Long.class, seed.horses().getLast(), hcm);
        // Luồng A gọi khi đơn rời chuyến: mọi chặng còn PLANNED của đơn bị hủy, xe được nhả chỗ
        assertThat(horseLegs.cancelPlannedLegsOfBooking(seed.bookingId())).isEqualTo(2 * seed.horses().size());
        assertThat(horseLegs.cancelPlannedLegsOfBooking(seed.bookingId())).isZero();
        assertThat(jdbc.queryForList("SELECT id FROM horse_stay WHERE id IN (?, ?)", Long.class, appointment, started)).containsExactly(started);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM horse_leg l JOIN booking_horse h ON h.id = l.booking_horse_id
                WHERE h.booking_id = ? AND l.stall_position IS NOT NULL
                """, Long.class, seed.bookingId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horse_leg l JOIN booking_horse bh ON bh.id = l.booking_horse_id WHERE bh.booking_id = ? AND l.status <> 'CANCELLED'",
                Long.class, seed.bookingId())).isZero();
        mvc.perform(get("/vehicle-trips/{id}/horse-legs", linehaul).header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void nextLegContinuesFromWhereAndWhenThePreviousLegEnded() throws Exception {
        Seed seed = seed("DEV-DOMESTIC-001");
        long danang = locations.findByCode("HUB-DN").orElseThrow().getId();
        // Lượt đón tới HUB-HCM 1 giờ sau giờ chạy trục: quá hạn có mặt ở hub
        Instant late = seed.departureAt().minusSeconds(3 * 3600);
        long lateTrip = tripId(createTrip(testData.vehicle(4, 0), "PICKUP", stop(hcm, "HUB", late, 0),
                address(seed.pickup(), "PICKUP", late, 60), stop(hcm, "HUB", late, 240)));
        List<Long> lateStops = stopIds(lateTrip);
        assign(lateTrip, seed.horses().getFirst(), lateStops.get(1), lateStops.get(2)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ngựa tới hub sau hạn có mặt của chuyến trục."));

        Instant onTime = seed.departureAt().minusSeconds(10 * 3600);
        long pickupTrip = tripId(createTrip(testData.vehicle(4, 0), "PICKUP", stop(hcm, "HUB", onTime, 0),
                address(seed.pickup(), "PICKUP", onTime, 60), stop(hcm, "HUB", onTime, 240)));
        List<Long> pickupStops = stopIds(pickupTrip);
        assign(pickupTrip, seed.horses().getFirst(), pickupStops.get(1), pickupStops.get(2)).andExpect(status().isCreated());

        // Chặng chuyển xe bắt đầu trước khi lượt đón về tới hub
        Instant early = onTime.plusSeconds(60 * 60);
        Vehicle earlyVehicle = testData.vehicle(4, 0);
        long earlyTransfer = tripId(createTrip(earlyVehicle, "TRANSFER", stop(hcm, "HUB", early, 0), stop(danang, "HUB", early, 900)));
        List<Long> earlyStops = stopIds(earlyTransfer);
        assign(earlyTransfer, seed.horses().getFirst(), earlyStops.get(0), earlyStops.get(1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Chặng mới bắt đầu trước khi chặng trước")));

        // Sau chặng đón con ngựa ở HUB-HCM, không bắt đầu chặng giao ở HUB-DN được
        Instant later = seed.departureAt().plusSeconds(3 * 24 * 3600);
        Vehicle atDanang = testData.vehicle(4, 0);
        long toDanang = tripId(createTrip(atDanang, "TRANSFER", stop(hcm, "HUB", later, 0), stop(danang, "HUB", later, 300)));
        long delivery = tripId(createTrip(atDanang, "DELIVERY", stop(danang, "HUB", later, 960),
                address(seed.delivery(), "DELIVERY", later, 1020), stop(danang, "HUB", later, 1200)));
        List<Long> deliveryStops = stopIds(delivery);
        assign(delivery, seed.horses().getFirst(), deliveryStops.get(0), deliveryStops.get(1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Chặng mới phải bắt đầu ở nơi chặng trước")));
        // Chuyển xe từ HUB-HCM tới HUB-DN thì nối tiếp được
        List<Long> transferStops = stopIds(toDanang);
        assign(toDanang, seed.horses().getFirst(), transferStops.get(0), transferStops.get(1)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].leg.legType").value("TRANSFER"));
        assign(delivery, seed.horses().getFirst(), deliveryStops.get(0), deliveryStops.get(1)).andExpect(status().isCreated());
    }

    @Test
    void vehicleSeatsAreNeverExceeded() throws Exception {
        Seed seed = seed("DEV-DOMESTIC-001");
        Instant start = seed.departureAt().minusSeconds(10 * 3600);
        long trip = tripId(createTrip(testData.vehicle(1, 0), "PICKUP", stop(hcm, "HUB", start, 0),
                address(seed.pickup(), "PICKUP", start, 90), stop(hcm, "HUB", start, 240)));
        List<Long> stops = stopIds(trip);
        assign(trip, seed.horses().getFirst(), stops.get(1), stops.get(2)).andExpect(status().isCreated());
        assign(trip, seed.horses().get(1), stops.get(1), stops.get(2)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", startsWith("Xe chỉ có 1 chỗ")));
    }

    @Test
    void exportBookingRidesPickupThenLinehaulToTheForeignHub() throws Exception {
        Seed seed = seed("DEV-EXPORT-001");
        long foreignHub = locations.findByCode("HUB-PNH").orElseThrow().getId();
        Instant start = seed.departureAt().minusSeconds(10 * 3600);
        long pickupTrip = tripId(createTrip(testData.vehicle(4, 0), "PICKUP", stop(hcm, "HUB", start, 0),
                address(seed.pickup(), "PICKUP", start, 90), stop(hcm, "HUB", start, 240)));
        List<Long> pickupStops = stopIds(pickupTrip);
        assign(pickupTrip, seed.horses().getFirst(), pickupStops.get(1), pickupStops.get(2)).andExpect(status().isCreated());

        long linehaul = tripId(createTrip(testData.vehicle(4, 0), "LINEHAUL", seed.departureId()));
        List<Long> linehaulStops = stopIds(linehaul);
        assign(linehaul, seed.horses().getFirst(), linehaulStops.getFirst(), linehaulStops.getLast()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].leg.toName").value(locations.findById(foreignHub).orElseThrow().getName()));

        // Lượt trục của chuyến khác không nhận ngựa của đơn này
        long otherDeparture = testData.departure("HCM-PNH", seed.pickupDate().plusDays(40), 9).getId();
        long otherTrip = tripId(createTrip(testData.vehicle(4, 0), "LINEHAUL", otherDeparture));
        List<Long> otherStops = stopIds(otherTrip);
        assign(otherTrip, seed.horses().getFirst(), otherStops.getFirst(), otherStops.getLast()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", startsWith("Đơn DEV-EXPORT-001 không đi chuyến")));
    }

    @Test
    void stopOfAnotherBookingCannotBeUsed() throws Exception {
        Seed direct = seed("DEV-DIRECT-001");
        Seed domestic = seed("DEV-DOMESTIC-001");
        Instant start = domestic.departureAt().minusSeconds(10 * 3600);
        long trip = tripId(createTrip(testData.vehicle(4, 0), "PICKUP", stop(hcm, "HUB", start, 0),
                address(direct.pickup(), "PICKUP", start, 60), address(domestic.pickup(), "PICKUP", start, 120), stop(hcm, "HUB", start, 240)));
        List<Long> stops = stopIds(trip);
        assign(trip, domestic.horses().getFirst(), stops.get(1), stops.get(3)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Điểm dừng 2 là địa chỉ của đơn khác."));
        mvc.perform(get("/vehicle-trips/{id}/horse-legs", trip).header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    private record Seed(long bookingId, LocalDate pickupDate, Long departureId, Instant departureAt, long pickup, long delivery, List<Long> horses) {}

    private Seed seed(String code) {
        var row = jdbc.queryForMap("""
                SELECT b.id, b.requested_pickup_date, b.departure_id, d.planned_departure_at FROM booking b
                LEFT JOIN departure d ON d.id = b.departure_id WHERE b.code = ?""", code);
        long id = ((Number) row.get("id")).longValue();
        long pickup = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'PICKUP'", Long.class, id);
        long delivery = jdbc.queryForObject("SELECT id FROM booking_address WHERE booking_id = ? AND address_type = 'DELIVERY'", Long.class, id);
        List<Long> horses = jdbc.queryForList("SELECT id FROM booking_horse WHERE booking_id = ? ORDER BY id", Long.class, id);
        Object departure = row.get("departure_id");
        Object departureAt = row.get("planned_departure_at");
        return new Seed(id, ((java.sql.Date) row.get("requested_pickup_date")).toLocalDate(),
                departure == null ? null : ((Number) departure).longValue(),
                departureAt == null ? null : toInstant(departureAt), pickup, delivery, horses);
    }

    private static Instant toInstant(Object value) {
        return value instanceof Timestamp ts ? ts.toInstant() : ((java.time.OffsetDateTime) value).toInstant();
    }

    private ResultActions createTrip(Vehicle vehicle, String type, String... stops) throws Exception {
        return mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripType\":\"%s\",\"vehicleId\":%d,\"stops\":[%s]}".formatted(type, vehicle.getId(), String.join(",", stops))));
    }

    private ResultActions createTrip(Vehicle vehicle, String type, long departureId) throws Exception {
        confirmRun(departureId);
        return mvc.perform(post("/vehicle-trips").header(HttpHeaders.AUTHORIZATION, coordinator).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripType\":\"%s\",\"vehicleId\":%d,\"departureId\":%d}".formatted(type, vehicle.getId(), departureId)));
    }

    private ResultActions assign(long trip, long bookingHorse, long board, long alight) throws Exception {
        return mvc.perform(post("/vehicle-trips/{id}/horse-legs", trip).header(HttpHeaders.AUTHORIZATION, coordinator)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingHorseId\":%d,\"boardTripStopId\":%d,\"alightTripStopId\":%d}".formatted(bookingHorse, board, alight)));
    }

    private String trip(long id) throws Exception {
        return mvc.perform(get("/vehicle-trips/{id}", id).header(HttpHeaders.AUTHORIZATION, coordinator))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private List<Long> stopIds(long tripId) throws Exception {
        List<Number> ids = JsonPath.read(trip(tripId), "$.data.stops[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private long legId(long bookingHorse, int sequence) {
        return jdbc.queryForObject("SELECT id FROM horse_leg WHERE booking_horse_id = ? AND sequence_number = ?", Long.class, bookingHorse, sequence);
    }

    private static String stop(long locationId, String type, Instant base, int minutes) {
        String at = base.plusSeconds(60L * minutes).toString();
        return "{\"locationId\":%d,\"stopType\":\"%s\",\"plannedArrivalAt\":\"%s\",\"plannedDepartureAt\":\"%s\"}".formatted(locationId, type, at, at);
    }

    private static String rest(long locationId, Instant base, int minutes) {
        return "{\"locationId\":%d,\"stopType\":\"REST\",\"plannedArrivalAt\":\"%s\",\"plannedDepartureAt\":\"%s\"}"
                .formatted(locationId, base.plusSeconds(60L * minutes), base.plusSeconds(60L * (minutes + 30)));
    }

    private static String address(long addressId, String type, Instant base, int minutes) {
        String at = base.plusSeconds(60L * minutes).toString();
        String leave = base.plusSeconds(60L * (minutes + 30)).toString();
        return "{\"bookingAddressId\":%d,\"stopType\":\"%s\",\"plannedArrivalAt\":\"%s\",\"plannedDepartureAt\":\"%s\"}".formatted(addressId, type, at, leave);
    }

    private static long tripId(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    // Quản lý đã xác nhận chạy chuyến (A6b): điều kiện để lập lượt trục
    private void confirmRun(long departureId) {
        entityManager.flush();
        jdbc.update("UPDATE departure SET run_confirmed_at = now(), run_confirmed_by = (SELECT min(id) FROM app_user) WHERE id = ? AND run_confirmed_at IS NULL", departureId);
        entityManager.clear();
    }
}
