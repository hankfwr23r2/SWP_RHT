package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Province;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.BookingRescheduleService;
import com.example.racehorse_transport.support.TestData;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
class DepartureMergeFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwt;
    @Autowired private TestData data;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private Clock clock;
    @Autowired private BookingRescheduleService reschedules;

    private LocalDate sourceDate;
    private String manager;

    @BeforeEach
    void prepareManagerAndDecisionDate() {
        sourceDate = LocalDate.ofInstant(clock.instant(), ZoneOffset.ofHours(7)).plusDays(1);
        manager = staffToken("MANAGER");
    }

    @Test
    void confirmsSmallerCapacityAndRecordsItInAuditAndDispatcherNotice() throws Exception {
        long source = departure("HCM-DN", sourceDate, 9, "CLOSED", true);
        attachBooking(source);
        var coordinator = data.staff(data.unique("g9-coord"), "COORDINATOR");

        mvc.perform(post("/departures/{id}/confirm-run", source).header(HttpHeaders.AUTHORIZATION, manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stallCapacity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stallCapacity").value(2))
                .andExpect(jsonPath("$.data.bookedStallCount").value(2))
                .andExpect(jsonPath("$.data.runConfirmedAt").isNotEmpty());

        entityManager.flush();
        assertThat(capacity(source)).isEqualTo(2);
        assertThat(jdbc.queryForMap("""
                SELECT (old_data ->> 'stallCapacity')::int AS old_capacity,
                       (new_data ->> 'stallCapacity')::int AS new_capacity
                FROM audit_log WHERE target_id = ? AND action = 'CONFIRM_DEPARTURE_RUN'
                """, source)).containsEntry("old_capacity", 9).containsEntry("new_capacity", 2);
        assertThat(jdbc.queryForObject("""
                SELECT n.message FROM notification n JOIN notification_recipient r ON r.notification_id = n.id
                WHERE r.user_id = ? AND n.title = 'Chuyến đã được xác nhận chạy'
                """, String.class, coordinator.getUserId())).contains("với 2 chỗ");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10})
    void rejectsCapacityBelowExistingReservationsOrAboveCurrentCapacity(int capacity) throws Exception {
        long source = departure("HCM-DN", sourceDate, 9, "CLOSED", true);
        attachBooking(source);

        mvc.perform(post("/departures/{id}/confirm-run", source).header(HttpHeaders.AUTHORIZATION, manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stallCapacity\":%d}".formatted(capacity)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.stallCapacity").value(containsString("từ 2 đến 9")));

        assertThat(capacity(source)).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT run_confirmed_at IS NULL FROM departure WHERE id = ?", Boolean.class, source)).isTrue();
    }

    @Test
    void keepsCapacityWhenConfirmingWithoutABody() throws Exception {
        long source = departure("HCM-DN", sourceDate, 9, "CLOSED", true);
        mvc.perform(post("/departures/{id}/confirm-run", source).header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stallCapacity").value(9));
    }

    @Test
    void mergesAndLetsCustomerAcceptThroughExistingEndpointAtTheSamePrice() throws Exception {
        LocalDate targetDate = sourceDate.plusDays(14);
        long target = departure("HCM-DN", targetDate, 9, "OPEN", true);
        long source = departure("HCM-DN", sourceDate, 9, "CLOSED", true);
        long adHoc = departure("HCM-DN", targetDate, 9, "OPEN", false);
        var booking = attachBooking(source);
        long quotation = recordDeposit(booking, sourceDate);
        String reason = "Dồn sang chuyến ngày " + targetDate + ": Ít khách";

        mvc.perform(get("/departures/{id}/merge-targets", source).header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(target)).isNotEmpty())
                .andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(adHoc)).isEmpty());
        merge(source, target, "Ít khách", manager).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertThat(mergedInto(source)).isEqualTo(target);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.SCHEDULE_CHANGE_REQUIRED);
        assertThat(reserved(source)).isEqualTo(2);
        assertThat(reserved(target)).isZero();
        assertThat(jdbc.queryForMap("""
                SELECT cause_type, reason, previous_departure_id FROM booking_status_history
                WHERE booking_id = ? AND to_status = 'SCHEDULE_CHANGE_REQUIRED'
                """, booking.getId())).containsEntry("cause_type", "COMPANY").containsEntry("reason", reason)
                .containsEntry("previous_departure_id", source);
        entityManager.flush();
        assertThat(jdbc.queryForObject("""
                SELECT n.message FROM notification n JOIN notification_recipient r ON r.notification_id = n.id
                WHERE r.user_id = ? AND n.booking_id = ? AND n.title = 'Cần chọn lại chuyến'
                """, String.class, booking.getCustomer().getUser().getId(), booking.getId())).isEqualTo(reason);

        suggestion(booking).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.departureId").value(target))
                .andExpect(jsonPath("$.data.departureDate").value(targetDate.toString()))
                .andExpect(jsonPath("$.data.requestedPickupDate").value(targetDate.minusDays(1).toString()));
        mvc.perform(post("/bookings/{id}/reschedule", booking.getId()).header(HttpHeaders.AUTHORIZATION, ownerToken(booking))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"departureId":%d,"requestedPickupDate":"%s"}
                                """.formatted(target, targetDate.minusDays(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.departureId").value(target));

        entityManager.flush();
        assertThat(reserved(source)).isZero();
        assertThat(reserved(target)).isEqualTo(2);
        assertThat(jdbc.queryForMap("SELECT id, total_amount FROM quotation WHERE booking_id = ? AND status = 'ACCEPTED'", booking.getId()))
                .containsEntry("id", quotation).containsEntry("total_amount", new BigDecimal("1000000.00"));
        assertThat(jdbc.queryForObject("""
                SELECT amount FROM payment WHERE quotation_id = ? AND payment_type = 'DEPOSIT' AND status = 'PAID'
                """, BigDecimal.class, quotation)).isEqualByComparingTo("300000");
        suggestion(booking).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void adminCanMergeIntoClosedDepartureButCustomerCannotAcceptItThroughTheSuggestion() throws Exception {
        long source = departure("HCM-DN", sourceDate, 9, "CLOSED", true);
        long target = departure("HCM-DN", sourceDate.plusDays(2), 9, "CLOSED", true);
        var booking = attachBooking(source);

        merge(source, target, "Ít khách", staffToken("ADMIN")).andExpect(status().isOk());
        assertThat(mergedInto(source)).isEqualTo(target);
        suggestion(booking).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void suggestsTheTargetFromHistoryForAnOrderAlreadyReleasedAtTheDecisionWindow() throws Exception {
        long source = departure("HCM-DN", sourceDate, 9, "CLOSED", true);
        long target = departure("HCM-DN", sourceDate.plusDays(14), 9, "OPEN", true);
        var booking = attachBooking(source, BookingStatus.PENDING_INTAKE);
        assertThat(reschedules.releaseAtDecision(booking.getId())).isTrue();
        assertThat(booking.getDeparture()).isNull();

        merge(source, target, "Ít khách", manager).andExpect(status().isOk());
        suggestion(booking).andExpect(status().isOk()).andExpect(jsonPath("$.data.departureId").value(target));
    }

    @ParameterizedTest
    @CsvSource({
            "DN-HCM, 9, 14, true, true, cùng tuyến",
            "HCM-DN, 1, 14, true, true, không đủ chỗ",
            "HCM-DN, 9, 31, true, true, hạn giữ giá",
            "HCM-DN, 9, 14, false, true, đột xuất",
            "HCM-DN, 9, 14, true, false, đột xuất"
    })
    void blocksInvalidMerges(String targetRoute, int capacity, int days, boolean scheduledSource, boolean scheduledTarget,
                             String message) throws Exception {
        long source = departure("HCM-DN", sourceDate, 9, "CLOSED", scheduledSource);
        long target = departure(targetRoute, sourceDate.plusDays(days), capacity, "OPEN", scheduledTarget);
        var booking = attachBooking(source);
        merge(source, target, "Ít khách", manager).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString(message)));
        assertStillOnSource(source, booking);
    }

    @Test
    void rejectsWhenAnOrderHasReceivedAHorseEvenBeforeItIsInOperation() throws Exception {
        long source = departure("HCM-DN", sourceDate, 9, "CLOSED", true);
        long target = departure("HCM-DN", sourceDate.plusDays(14), 9, "OPEN", true);
        var booking = attachBooking(source);
        receiveHorse(booking);

        merge(source, target, "Ít khách", manager).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("đã nhận ngựa")));
        assertStillOnSource(source, booking);
    }

    @Test
    void rejectsAnEmptyReasonAndAnyoneOtherThanAdministratorOrManager() throws Exception {
        long source = departure("HCM-DN", sourceDate, 9, "CLOSED", true);
        long target = departure("HCM-DN", sourceDate.plusDays(14), 9, "OPEN", true);
        var booking = attachBooking(source);

        merge(source, target, "Ít khách", ownerToken(booking)).andExpect(status().isForbidden());
        merge(source, target, "Ít khách", staffToken("COORDINATOR")).andExpect(status().isForbidden());
        merge(source, target, "", manager).andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.reason").isNotEmpty());
        assertStillOnSource(source, booking);
    }

    private String staffToken(String role) {
        String username = data.unique("g9-staff");
        data.staff(username, role);
        return "Bearer " + jwt.generateToken(username, List.of(role));
    }

    private String ownerToken(Booking booking) {
        return "Bearer " + jwt.generateToken(booking.getCustomer().getUser().getUsername(), List.of("CUSTOMER"));
    }

    private ResultActions merge(long source, long target, String reason, String token) throws Exception {
        return mvc.perform(post("/departures/{id}/merge", source).header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"targetDepartureId":%d,"reason":"%s"}
                        """.formatted(target, reason)));
    }

    private ResultActions suggestion(Booking booking) throws Exception {
        return mvc.perform(get("/bookings/{id}/reschedule-suggestion", booking.getId())
                .header(HttpHeaders.AUTHORIZATION, ownerToken(booking)));
    }

    private long departure(String routeCode, LocalDate date, int capacity, String status, boolean scheduled) {
        long route = jdbc.queryForObject("SELECT id FROM route WHERE code = ?", Long.class, routeCode);
        Long schedule = null;
        if (scheduled) {
            schedule = jdbc.queryForObject("""
                    INSERT INTO departure_schedule (route_id, departure_time, booking_cutoff_hours, stall_capacity, valid_from, status)
                    VALUES (?, TIME '06:00', 72, ?, ?, 'ACTIVE') RETURNING id
                    """, Long.class, route, capacity, date);
            jdbc.update("INSERT INTO departure_schedule_day (departure_schedule_id, day_of_week) VALUES (?, ?)",
                    schedule, date.getDayOfWeek().getValue());
        }
        var planned = date.atTime(6, 0).atOffset(ZoneOffset.ofHours(7));
        return jdbc.queryForObject("""
                INSERT INTO departure (departure_schedule_id, route_id, departure_date, planned_departure_at,
                                       booking_cutoff_at, stall_capacity, status)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, schedule, route, date, planned, planned.minusDays(3), capacity, status);
    }

    private Booking attachBooking(long departureId) {
        return attachBooking(departureId, BookingStatus.CONFIRMED);
    }

    private Booking attachBooking(long departureId, BookingStatus status) {
        var departure = entityManager.find(Departure.class, departureId);
        var owner = data.customer(data.unique("g9-client"));
        var booking = data.booking(owner, BookingType.INTERPROVINCE, status);
        booking.setDeparture(departure);
        booking.setRequestedPickupDate(departure.getDepartureDate().minusDays(1));
        if (status == BookingStatus.CONFIRMED) {
            booking.setWaybillNumber(data.unique("WB"));
        }
        data.bookingAddress(booking, AddressRole.PICKUP).setProvince(entityManager.getReference(Province.class, "VN-79"));
        data.bookingAddress(booking, AddressRole.DELIVERY);
        long horse = jdbc.queryForObject("""
                INSERT INTO horse (owner_customer_id, name, sex, horse_type_id, status)
                VALUES (?, 'Ngựa thử 3.G9', 'MARE', (SELECT min(id) FROM horse_type), 'ACTIVE') RETURNING id
                """, Long.class, owner.getUser().getId());
        jdbc.update("INSERT INTO booking_horse (booking_id, horse_id, stall_type_code, status) VALUES (?, ?, 'WIDE', 'ACTIVE')",
                booking.getId(), horse);
        departure.setBookedStallCount(2);
        entityManager.flush();
        return booking;
    }

    private long recordDeposit(Booking booking, LocalDate departureDate) {
        long quote = jdbc.queryForObject("""
                INSERT INTO quotation (booking_id, version, price_list_id, currency_code, deposit_rate, expires_at,
                                       confirmed_pickup_date, confirmed_departure_date, estimated_delivery_date,
                                       total_amount, pass_through_amount, deposit_amount, status, sent_by, sent_at, accepted_at)
                VALUES (?, 1, (SELECT min(id) FROM price_list), 'VND', 0.3, now() + interval '1 day',
                        ?, ?, ?, 1000000, 0, 300000, 'ACCEPTED',
                        (SELECT id FROM app_user WHERE username = 'manager.dev'), now(), now()) RETURNING id
                """, Long.class, booking.getId(), booking.getRequestedPickupDate(), departureDate, departureDate.plusDays(1));
        jdbc.update("""
                INSERT INTO quotation_line (quotation_id, line_number, line_type, description, quantity, unit, unit_price, is_pass_through)
                VALUES (?, 1, 'ADJUSTMENT', 'Cước theo thỏa thuận', 1, 'ORDER', 1000000, false)
                """, quote);
        jdbc.update("""
                INSERT INTO payment (booking_id, quotation_id, payment_type, amount, currency_code, payment_method, status, paid_at)
                VALUES (?, ?, 'DEPOSIT', 300000, 'VND', 'VNPAY', 'PAID', now())
                """, booking.getId(), quote);
        return quote;
    }

    private void receiveHorse(Booking booking) {
        long horse = jdbc.queryForObject("SELECT id FROM booking_horse WHERE booking_id = ?", Long.class, booking.getId());
        long leg = jdbc.queryForObject("""
                WITH test_trip AS (INSERT INTO vehicle_trip (code, vehicle_id, start_location_id, end_location_id, trip_type, status, planned_start_at, planned_end_at) SELECT 'TEST-' || left(md5(random()::text), 20), min(v.id), min(l.id), min(l.id), 'TRANSFER', 'COMPLETED', now(), now() + interval '1 hour' FROM vehicle v CROSS JOIN location l WHERE l.location_type = 'HUB' RETURNING id) INSERT INTO horse_leg (booking_horse_id, sequence_number, leg_type, from_location_id, to_location_id,
                                       vehicle_trip_id, planned_start_at, planned_end_at, status)
                VALUES (?, 1, 'PICKUP', (SELECT id FROM location WHERE code = 'HUB-HCM'),
                        (SELECT id FROM location WHERE code = 'HUB-DN'), (SELECT id FROM test_trip),
                        now(), now() + interval '1 hour', 'COMPLETED') RETURNING id
                """, Long.class, horse);
        long stay = jdbc.queryForObject("""
                INSERT INTO horse_stay (booking_horse_id, stay_type, location_id, planned_start_at, planned_end_at)
                VALUES (?, 'ASSEMBLY', (SELECT id FROM location WHERE code = 'HUB-HCM'), now(), now() + interval '1 hour')
                RETURNING id
                """, Long.class, horse);
        long handover = jdbc.queryForObject("""
                INSERT INTO handover (handover_type, horse_stay_id, handled_by, from_party_id, status, handed_at)
                VALUES ('PICKUP', ?, (SELECT id FROM app_user WHERE username = 'manager.dev'),
                        (SELECT min(id) FROM party), 'DONE', now()) RETURNING id
                """, Long.class, stay);
        jdbc.update("INSERT INTO handover_horse (handover_id, horse_leg_id, is_microchip_matched) VALUES (?, ?, true)", handover, leg);
    }

    private void assertStillOnSource(long source, Booking booking) {
        assertThat(mergedInto(source)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM departure WHERE id = ?", String.class, source)).isEqualTo("CLOSED");
        assertThat(booking.getDeparture().getId()).isEqualTo(source);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    private Long mergedInto(long source) {
        return jdbc.queryForObject("SELECT merged_into_departure_id FROM departure WHERE id = ?", Long.class, source);
    }

    private int reserved(long departure) {
        return jdbc.queryForObject("SELECT booked_stall_count FROM departure WHERE id = ?", Integer.class, departure);
    }

    private int capacity(long departure) {
        return jdbc.queryForObject("SELECT stall_capacity FROM departure WHERE id = ?", Integer.class, departure);
    }
}
