package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import com.example.racehorse_transport.support.TestData;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class DepartureDecisionFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwt;
    @Autowired private TestData data;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private Clock clock;

    private LocalDate departureDate;
    private String manager;

    @BeforeEach
    void prepareManagerAndDecisionDate() {
        departureDate = LocalDate.ofInstant(clock.instant(), ZoneOffset.ofHours(7)).plusDays(1);
        manager = staffToken("MANAGER");
    }

    @Test
    void confirmsRunAndRecordsCapacityInAuditAndDispatcherNotice() throws Exception {
        long source = departure("HCM-DN", departureDate, 9, "CLOSED");
        attachBooking(source);
        var coordinator = data.staff(data.unique("g9-coord"), "COORDINATOR");

        mvc.perform(post("/departures/{id}/confirm-run", source).header(HttpHeaders.AUTHORIZATION, manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stallCapacity").value(9))
                .andExpect(jsonPath("$.data.bookedStallCount").value(2))
                .andExpect(jsonPath("$.data.runConfirmedAt").isNotEmpty());

        entityManager.flush();
        assertThat(capacity(source)).isEqualTo(9);
        assertThat(jdbc.queryForMap("""
                SELECT (old_data ->> 'stallCapacity')::int AS old_capacity,
                       (new_data ->> 'stallCapacity')::int AS new_capacity
                FROM audit_log WHERE target_id = ? AND action = 'CONFIRM_DEPARTURE_RUN'
                """, source)).containsEntry("old_capacity", 9).containsEntry("new_capacity", 9);
        assertThat(jdbc.queryForObject("""
                SELECT n.message FROM notification n JOIN notification_recipient r ON r.notification_id = n.id
                WHERE r.user_id = ? AND n.title = 'Chuyến đã được xác nhận chạy'
                """, String.class, coordinator.getUserId())).contains("với 9 chỗ");
    }

    @Test
    void keepsCapacityWhenConfirmingWithoutABody() throws Exception {
        long source = departure("HCM-DN", departureDate, 9, "CLOSED");
        mvc.perform(post("/departures/{id}/confirm-run", source).header(HttpHeaders.AUTHORIZATION, manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stallCapacity").value(9));
    }

    @Test
    void refusesToCancelADepartureOnceAnOrderHasHandedOverAHorse() throws Exception {
        long source = departure("HCM-DN", departureDate, 9, "CLOSED");
        var booking = attachBooking(source);
        receiveHorse(booking);

        mvc.perform(post("/departures/{id}/cancel", source).header(HttpHeaders.AUTHORIZATION, manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Ít khách\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("đã nhận ngựa")));

        assertThat(jdbc.queryForObject("SELECT status FROM departure WHERE id = ?", String.class, source)).isEqualTo("CLOSED");
        assertThat(booking.getDeparture().getId()).isEqualTo(source);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    private String staffToken(String role) {
        String username = data.unique("g9-staff");
        data.staff(username, role);
        return "Bearer " + jwt.generateToken(username, List.of(role), 0);
    }

    private long departure(String routeCode, LocalDate date, int capacity, String status) {
        long route = jdbc.queryForObject("SELECT id FROM route WHERE code = ?", Long.class, routeCode);
        var planned = date.atTime(6, 0).atOffset(ZoneOffset.ofHours(7));
        return jdbc.queryForObject("""
                INSERT INTO departure (route_id, departure_date, planned_departure_at, booking_cutoff_at, stall_capacity, status)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, route, date, planned, planned.minusDays(3), capacity, status);
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

    private int capacity(long departure) {
        return jdbc.queryForObject("SELECT stall_capacity FROM departure WHERE id = ?", Integer.class, departure);
    }
}
