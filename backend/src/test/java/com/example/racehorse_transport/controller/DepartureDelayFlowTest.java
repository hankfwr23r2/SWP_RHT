package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.dto.departure.DepartureDelayRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.DepartureDelayService;
import com.example.racehorse_transport.support.TestData;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class DepartureDelayFlowTest {
    @Autowired private DepartureDelayService delays;
    @Autowired private DepartureRepository departures;
    @Autowired private BookingRepository bookings;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private Clock clock;

    @Test
    void remindsAndMarksDelayOnceAndLeavesAnAlreadyRunningDepartureAlone() {
        var near = departureAt(clock.instant().plus(Duration.ofHours(23)));
        var late = departureAt(clock.instant().minus(Duration.ofMinutes(31)));
        var customer = attach(late);
        var inProgress = departureAt(clock.instant().minus(Duration.ofHours(6)));
        inProgress.setStatus(DepartureStatus.IN_PROGRESS);
        long manager = data.staff(data.unique("g3-manager"), "MANAGER").getUserId();
        long coordinator = data.staff(data.unique("g3-coordinator"), "COORDINATOR").getUserId();
        delays.scan();
        delays.scan();
        entityManager.flush();
        assertThat(near.getRunRemindedAt()).isNotNull();
        assertThat(late.getDelayedAt()).isNotNull();
        assertThat(inProgress.getStatus()).isEqualTo(DepartureStatus.IN_PROGRESS);
        assertThat(inProgress.getDelayedAt()).isNull();
        assertThat(customerNotices(customer.getId(), "Chuyến bị chậm")).isEqualTo(1);
        assertThat(staffNotices(manager, "Chuyến chưa xác nhận chạy")).isPositive();
        assertThat(staffNotices(coordinator, "Chuyến chưa xác nhận chạy")).isPositive();
    }

    @Test
    void reportsWholeTripDelayOnlyToCoordinatorsAndOrdersStillOnBoard() throws Exception {
        var departure = departureAt(clock.instant().minus(Duration.ofHours(6)));
        departure.setStatus(DepartureStatus.IN_PROGRESS);
        var twoHorses = attach(departure);
        var oneHorse = attach(departure);
        var alreadyOff = attach(departure);
        var notYetOn = attach(departure);
        String driverName = data.unique("g3-driver");
        long driverId = data.staff(driverName, "DRIVER").getUserId();
        long coordinator = data.staff(data.unique("g3-coordinator"), "COORDINATOR").getUserId();
        long manager = data.staff(data.unique("g3-manager"), "MANAGER").getUserId();
        long trip = runningTrip(departure.getId(), driverId);
        long board = tripStop(trip, 1, "HUB-HCM");
        long alight = tripStop(trip, 2, "HUB-DN");
        addTripLeg(horseFor(twoHorses), trip, board, alight, "IN_PROGRESS");
        addTripLeg(horseFor(twoHorses), trip, board, alight, "IN_PROGRESS");
        addTripLeg(horseFor(oneHorse), trip, board, alight, "IN_PROGRESS");
        addTripLeg(horseFor(alreadyOff), trip, board, alight, "COMPLETED");
        addTripLeg(horseFor(notYetOn), trip, board, alight, "PLANNED");

        String body = """
                {"vehicleTripId":%d,"incidentType":"DELAY","causeType":"COMPANY_FAULT",
                 "title":"Trễ ở điểm Route 1","description":"Xe đang chậm so với kế hoạch."}
                """.formatted(trip);
        String driverToken = "Bearer " + jwt.generateToken(driverName, List.of("DRIVER"));
        mvc.perform(post("/incidents").header(HttpHeaders.AUTHORIZATION, driverToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.departureId").value(departure.getId()))
                .andExpect(jsonPath("$.data.vehicleTripId").value(trip));
        mvc.perform(post("/incidents").header(HttpHeaders.AUTHORIZATION, driverToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        entityManager.flush();
        assertThat(staffNotices(coordinator, "Sự cố mới")).isEqualTo(1);
        assertThat(staffNotices(manager, "Sự cố mới")).isZero();
        assertTripDelayNotice(twoHorses);
        assertTripDelayNotice(oneHorse);
        assertThat(customerNotices(alreadyOff.getId(), "Xe đang chạy bị chậm")).isZero();
        assertThat(customerNotices(notYetOn.getId(), "Xe đang chạy bị chậm")).isZero();
        assertThat(departure.getStatus()).isEqualTo(DepartureStatus.IN_PROGRESS);
        assertThat(departure.getAutoCancelledAt()).isNull();
    }

    @Test
    void mixedDepartureStaysLateAndOnlyUnreceivedBookingCanRequestRefund() throws Exception {
        var departure = departureAt(clock.instant().minus(Duration.ofHours(6)));
        Booking received = attach(departure);
        Booking notReceived = attach(departure);
        long horse = horseFor(received);
        receiveHorse(horse);
        long managerId = data.staff(data.unique("g3-manager"), "MANAGER").getUserId();

        delays.scan();
        delays.scan();
        entityManager.flush();
        assertThat(received.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(departure.getAutoCancelledAt()).isNull();
        assertThat(departure.getDelayEscalatedAt()).isNotNull();
        assertThat(staffNotices(managerId, "Cần xử lý chuyến chậm đã nhận ngựa")).isEqualTo(1);
        options(received).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].requiresManagerContact").value(true))
                .andExpect(jsonPath("$.data[0].canRefundFull").value(false));
        options(notReceived).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].canRescheduleFree").value(true))
                .andExpect(jsonPath("$.data[0].canRefundFull").value(true));
        mvc.perform(get("/bookings/{id}/delay/refund-preview", received.getId())
                        .header(HttpHeaders.AUTHORIZATION, ownerToken(received)))
                .andExpect(status().isConflict());
        mvc.perform(post("/bookings/{id}/delay/refund", received.getId())
                        .header(HttpHeaders.AUTHORIZATION, ownerToken(received)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Không đi nữa\"}"))
                .andExpect(status().isConflict());
    }

    @ParameterizedTest
    @CsvSource({"0, 450000", "770000, 1220000"})
    void automaticCancellationOnceAndCompanyRefundIncludesAllPaidAmountsAndReleasesTheStall(int balancePaid, int refundAmount)
            throws Exception {
        var departure = departureAt(clock.instant().minus(Duration.ofHours(6)));
        var booking = attach(departure);
        departure.setBookedStallCount(1);
        long horse = horseFor(booking);
        long leg = addLeg(horse, "PLANNED");
        payBookingWithPassThrough(booking.getId(), balancePaid);
        delays.scan();
        Instant cancelled = departure.getAutoCancelledAt();
        delays.scan();
        entityManager.flush();
        assertThat(cancelled).isNotNull().isEqualTo(departure.getAutoCancelledAt());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.SCHEDULE_CHANGE_REQUIRED);
        assertThat(customerNotices(booking.getId(), "Chuyến đã hủy do chậm")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM horse_leg WHERE id = ?", String.class, leg)).isEqualTo("CANCELLED");
        options(booking).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].canRefundFull").value(true));
        mvc.perform(get("/bookings/{id}/delay/refund-preview", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, ownerToken(booking))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowed").value(true))
                .andExpect(jsonPath("$.data.refundable").value(refundAmount));
        var refund = post("/bookings/{id}/delay/refund", booking.getId())
                .header(HttpHeaders.AUTHORIZATION, ownerToken(booking)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Chọn hoàn do chuyến bị hủy\"}");
        mvc.perform(refund).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refundable").value(refundAmount));
        mvc.perform(refund).andExpect(status().isConflict());
        entityManager.flush();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(departure.getBookedStallCount()).isZero();
        assertThat(booking.getDeparture()).isNull();
        var refundedTypes = jdbc.query("""
                SELECT paid.payment_type, paid.amount AS original_amount, refund.amount
                FROM payment refund
                JOIN payment paid ON paid.id = refund.refunded_payment_id AND paid.booking_id = refund.booking_id
                WHERE refund.booking_id = ? AND refund.payment_type = 'REFUND' AND refund.status = 'PENDING'
                ORDER BY paid.id
                """, (row, index) -> {
                    assertThat(row.getBigDecimal("amount")).isEqualByComparingTo(row.getBigDecimal("original_amount"));
                    return row.getString("payment_type");
                }, booking.getId());
        assertThat(refundedTypes).containsExactlyElementsOf(balancePaid == 0 ? List.of("DEPOSIT") : List.of("DEPOSIT", "BALANCE"));
    }

    @Test
    void managerRevisionsExposeEachCustomerThresholdFromTheOriginalDepartureTime() throws Exception {
        var departure = departureAt(clock.instant().plus(Duration.ofHours(8)));
        var booking = attach(departure);
        String managerName = data.unique("g3-manager");
        data.staff(managerName, "MANAGER");
        String managerToken = "Bearer " + jwt.generateToken(managerName, List.of("MANAGER"));
        Instant original = departure.getPlannedDepartureAt();
        for (int hours : List.of(1, 2, 5)) {
            Instant revised = original.plus(Duration.ofHours(hours));
            mvc.perform(post("/departures/{id}/delay", departure.getId())
                            .header(HttpHeaders.AUTHORIZATION, managerToken).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"revisedDepartureAt":"%s","reason":"Điều xe để chạy"}
                                    """.formatted(revised)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.plannedDepartureAt").value(original.toString()))
                    .andExpect(jsonPath("$.data.revisedDepartureAt").value(revised.toString()));
            options(booking).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].canRescheduleFree").value(hours >= 2))
                    .andExpect(jsonPath("$.data[0].canRefundFull").value(hours >= 5));
        }
    }

    @Test
    void customerCannotReviseAndCannotReadAnotherCustomersDelay() throws Exception {
        var departure = departureAt(clock.instant().plus(Duration.ofHours(8)));
        var booking = attach(departure);
        var stranger = attach(departure);
        mvc.perform(get("/bookings/{id}/delay", booking.getId()).header(HttpHeaders.AUTHORIZATION, ownerToken(stranger)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/departures/{id}/delay", departure.getId()).header(HttpHeaders.AUTHORIZATION, ownerToken(booking))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"revisedDepartureAt\":\"2099-01-01T00:00:00Z\",\"reason\":\"Xe hỏng\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRevisionsAndCancellationCannotBothWin() throws Exception {
        var departure = departureAt(clock.instant().minus(Duration.ofHours(6)));
        var begin = new CountDownLatch(1);
        try (var tasks = Executors.newFixedThreadPool(3)) {
            var first = tasks.submit(() -> reviseAfter(begin, departure, 1));
            var second = tasks.submit(() -> reviseAfter(begin, departure, 2));
            var scan = tasks.submit(() -> { begin.await(); delays.scan(); return true; });
            begin.countDown();
            boolean revised = first.get(30, TimeUnit.SECONDS) | second.get(30, TimeUnit.SECONDS);
            scan.get(30, TimeUnit.SECONDS);
            var saved = departures.findById(departure.getId()).orElseThrow();
            assertThat(saved.getPlannedDepartureAt()).isEqualTo(departure.getPlannedDepartureAt());
            if (revised) {
                assertThat(saved.getRevisedDepartureAt()).isNotNull();
                assertThat(saved.getAutoCancelledAt()).isNull();
            } else {
                assertThat(saved.getStatus()).isEqualTo(DepartureStatus.CANCELLED);
                assertThat(saved.getAutoCancelledAt()).isNotNull();
                assertThat(saved.getRevisedDepartureAt()).isNull();
            }
        }
    }

    private boolean reviseAfter(CountDownLatch begin, Departure departure, int hours) throws InterruptedException {
        begin.await();
        try {
            delays.revise(departure.getId(), new DepartureDelayRequestDto(clock.instant().plus(Duration.ofHours(hours)), "Xe đổi"));
            return true;
        } catch (BookingRuleException cancelled) {
            return false;
        }
    }

    private org.springframework.test.web.servlet.ResultActions options(Booking booking) throws Exception {
        return mvc.perform(get("/bookings/{id}/delay", booking.getId()).header(HttpHeaders.AUTHORIZATION, ownerToken(booking)));
    }

    private String ownerToken(Booking booking) {
        return "Bearer " + jwt.generateToken(booking.getCustomer().getUser().getUsername(), List.of("CUSTOMER"));
    }

    private Departure departureAt(Instant instant) {
        var departure = data.departure("HCM-DN", LocalDate.ofInstant(instant, ZoneOffset.UTC), 9);
        departure.setPlannedDepartureAt(instant.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        departure.setBookingCutoffAt(departure.getPlannedDepartureAt().minus(Duration.ofDays(3)));
        departure.setStatus(DepartureStatus.CLOSED);
        return departures.saveAndFlush(departure);
    }

    private Booking attach(Departure departure) {
        var customer = data.customer(data.unique("g3-customer"));
        var booking = data.booking(customer, BookingType.INTERPROVINCE, BookingStatus.CONFIRMED);
        booking.setDeparture(departure);
        return bookings.saveAndFlush(booking);
    }

    private long horseFor(Booking booking) {
        long horse = jdbc.queryForObject("""
                INSERT INTO horse (owner_customer_id, name, sex, horse_type_id, status)
                VALUES (?, 'Ngựa thử G3', 'MARE', (SELECT min(id) FROM horse_type), 'ACTIVE') RETURNING id
                """, Long.class, booking.getCustomer().getUser().getId());
        return jdbc.queryForObject("""
                INSERT INTO booking_horse (booking_id, horse_id, stall_type_code, status)
                VALUES (?, ?, 'STANDARD', 'ACTIVE') RETURNING id
                """, Long.class, booking.getId(), horse);
    }

    private long addLeg(long horse, String status) {
        return jdbc.queryForObject("""
                INSERT INTO horse_leg (booking_horse_id, sequence_number, leg_type, from_location_id, to_location_id,
                                       agent_party_id, planned_start_at, planned_end_at, status)
                VALUES (?, 1, 'PICKUP', (SELECT id FROM location WHERE code = 'HUB-HCM'),
                        (SELECT id FROM location WHERE code = 'HUB-DN'), (SELECT min(id) FROM party),
                        now(), now() + interval '1 hour', ?) RETURNING id
                """, Long.class, horse, status);
    }

    private void receiveHorse(long horse) {
        long leg = addLeg(horse, "COMPLETED");
        long stay = jdbc.queryForObject("""
                INSERT INTO horse_stay (booking_horse_id, stay_type, location_id, planned_start_at, planned_end_at)
                VALUES (?, 'ASSEMBLY', (SELECT id FROM location WHERE code = 'HUB-HCM'), now(), now() + interval '1 hour')
                RETURNING id
                """, Long.class, horse);
        long handover = jdbc.queryForObject("""
                INSERT INTO handover (handover_type, horse_stay_id, handled_by, status, handed_at)
                VALUES ('PICKUP', ?, (SELECT id FROM app_user WHERE username = 'manager.dev'), 'DONE', now()) RETURNING id
                """, Long.class, stay);
        jdbc.update("INSERT INTO handover_horse (handover_id, horse_leg_id, is_microchip_matched) VALUES (?, ?, true)", handover, leg);
    }

    private long runningTrip(long departure, long driver) {
        long vehicle = data.vehicle(6, 0).getId();
        long trip = jdbc.queryForObject("""
                INSERT INTO vehicle_trip (code, trip_type, departure_id, vehicle_id, start_location_id, end_location_id,
                                          planned_start_at, planned_end_at, actual_start_at, status)
                SELECT ?, 'LINEHAUL', d.id, ?, r.origin_location_id, r.destination_location_id,
                       now() - interval '1 hour', now() + interval '2 hours', now() - interval '1 hour', 'IN_PROGRESS'
                FROM departure d JOIN route r ON r.id = d.route_id WHERE d.id = ? RETURNING id
                """, Long.class, data.unique("TRIP"), vehicle, departure);
        jdbc.update("""
                INSERT INTO crew_assignment (vehicle_trip_id, staff_id, crew_role, status)
                VALUES (?, ?, 'DRIVER', 'ACKNOWLEDGED')
                """, trip, driver);
        return trip;
    }

    private long tripStop(long trip, int sequence, String location) {
        return jdbc.queryForObject("""
                INSERT INTO trip_stop (vehicle_trip_id, sequence_number, location_id, stop_type, planned_arrival_at, planned_departure_at)
                SELECT ?, ?, id, 'HUB', now() + ? * interval '1 hour', now() + ? * interval '1 hour'
                FROM location WHERE code = ? RETURNING id
                """, Long.class, trip, sequence, sequence - 2, sequence - 2, location);
    }

    private void addTripLeg(long horse, long trip, long board, long alight, String status) {
        jdbc.update("""
                INSERT INTO horse_leg (booking_horse_id, sequence_number, leg_type, from_location_id, to_location_id,
                                       vehicle_trip_id, board_trip_stop_id, alight_trip_stop_id, planned_start_at, planned_end_at, status)
                SELECT ?, 1, 'LINEHAUL', origin.location_id, destination.location_id, ?, origin.id, destination.id,
                       origin.planned_departure_at, destination.planned_arrival_at, ?
                FROM trip_stop origin, trip_stop destination WHERE origin.id = ? AND destination.id = ?
                """, horse, trip, status, board, alight);
    }

    private void assertTripDelayNotice(Booking booking) {
        assertThat(jdbc.queryForList("""
                SELECT n.link_url FROM notification n JOIN notification_recipient r ON r.notification_id = n.id
                WHERE n.booking_id = ? AND r.user_id = ? AND n.title = 'Xe đang chạy bị chậm'
                """, String.class, booking.getId(), booking.getCustomer().getUser().getId()))
                .containsExactly("/orders/" + booking.getId());
    }

    private int customerNotices(long bookingId, String title) {
        return jdbc.queryForObject("SELECT count(*) FROM notification WHERE booking_id = ? AND title = ?", Integer.class, bookingId, title);
    }

    private int staffNotices(long userId, String title) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM notification n JOIN notification_recipient r ON r.notification_id = n.id
                WHERE r.user_id = ? AND n.title = ?
                """, Integer.class, userId, title);
    }

    private void payBookingWithPassThrough(long bookingId, int balancePaid) {
        long quote = jdbc.queryForObject("""
                INSERT INTO quotation (booking_id, version, price_list_id, currency_code, deposit_rate, expires_at,
                                       confirmed_pickup_date, confirmed_departure_date, estimated_delivery_date,
                                       total_amount, pass_through_amount, deposit_amount, status, sent_by, sent_at, accepted_at)
                VALUES (?, 1, (SELECT min(id) FROM price_list), 'VND', 0.3, now() + interval '1 day',
                        CURRENT_DATE + 1, CURRENT_DATE + 2, CURRENT_DATE + 3, 1220000, 120000, 450000, 'ACCEPTED',
                        (SELECT id FROM app_user WHERE username = 'manager.dev'), now(), now()) RETURNING id
                """, Long.class, bookingId);
        jdbc.update("""
                INSERT INTO payment (booking_id, quotation_id, payment_type, amount, currency_code, payment_method, status, paid_at)
                VALUES (?, ?, 'DEPOSIT', 450000, 'VND', 'VNPAY', 'PAID', now())
                """, bookingId, quote);
        if (balancePaid > 0) {
            jdbc.update("""
                    INSERT INTO payment (booking_id, quotation_id, payment_type, amount, currency_code, payment_method, status, paid_at)
                    VALUES (?, ?, 'BALANCE', ?, 'VND', 'VNPAY', 'PAID', now())
                    """, bookingId, quote, balancePaid);
        }
    }
}
