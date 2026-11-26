package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.repository.VehicleClassRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
class LinehaulAllocationFlowTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entities;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;
    @Autowired private VehicleClassRepository classes;

    @Test
    void suggestsTwoClustersAndCreatesTripsWithExactHorseAssignments() throws Exception {
        var scenario = scenario();
        mvc.perform(get(scenario.path() + "-suggestion").header(HttpHeaders.AUTHORIZATION, scenario.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.missingSeats").value(0))
                .andExpect(jsonPath("$.data.trucks[0].capacity").value(6))
                .andExpect(jsonPath("$.data.trucks[0].horses.length()").value(3))
                .andExpect(jsonPath("$.data.trucks[0].horses[0].bookingHorseId").value(scenario.far().getFirst()))
                .andExpect(jsonPath("$.data.trucks[1].capacity").value(9))
                .andExpect(jsonPath("$.data.trucks[1].horses.length()").value(8));
        assertThat(trips(scenario)).isZero();

        mvc.perform(post(scenario.path() + "-trips").header(HttpHeaders.AUTHORIZATION, scenario.token())
                .contentType(MediaType.APPLICATION_JSON).content(body(scenario, scenario.near())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));

        assertThat(trips(scenario)).isEqualTo(2);
        assertThat(assigned(scenario.medium())).containsExactlyInAnyOrderElementsOf(scenario.far());
        assertThat(assigned(scenario.heavy())).containsExactlyInAnyOrderElementsOf(scenario.near());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM horse_leg l JOIN vehicle_trip t ON t.id = l.vehicle_trip_id
                WHERE t.departure_id = ? AND l.stall_position IS NOT NULL
                """, Integer.class, scenario.departure())).isEqualTo(11);
    }

    @Test
    void rejectsPreviewThatWouldLeaveOutAHorseFromOneBooking() throws Exception {
        var scenario = scenario();
        mvc.perform(post(scenario.path() + "-trips").header(HttpHeaders.AUTHORIZATION, scenario.token())
                .contentType(MediaType.APPLICATION_JSON).content(body(scenario, scenario.near().subList(0, 7))))
                .andExpect(status().isConflict());
        assertThat(trips(scenario)).isZero();
    }

    private Scenario scenario() {
        String coordinator = data.unique("m4");
        var staff = data.staff(coordinator, "COORDINATOR");
        var customer = data.customer(data.unique("m4-customer"));
        var departure = data.departure("HCM-DN", LocalDate.of(2031, 3, 20), 15);
        departure.setRunConfirmedAt(departure.getBookingCutoffAt());
        departure.setRunConfirmedBy(staff.getUser());
        departure.setBookedStallCount(11);
        var heavy = data.vehicle(9, 0);
        var medium = data.vehicle(6, 0);
        medium.setVehicleClass(classes.findByCode("MEDIUM").orElseThrow());
        var near = new ArrayList<>(booking(customer, departure, 5, "16.05", "108.20"));
        near.addAll(booking(customer, departure, 3, "16.10", "108.18"));
        var far = booking(customer, departure, 3, "17.48", "106.60");
        entities.flush();
        long base = allotment(departure.getId(), heavy.getVehicleClass().getId(), "BASE");
        long spare = allotment(departure.getId(), medium.getVehicleClass().getId(), "SPARE");
        return new Scenario(departure.getId(), "Bearer " + jwt.generateToken(coordinator, List.of("COORDINATOR")),
                base, spare, heavy.getId(), medium.getId(), near, far);
    }

    private List<Long> booking(Customer customer, Departure departure, int count, String latitude, String longitude) {
        var booking = data.booking(customer, BookingType.INTERPROVINCE, BookingStatus.CONFIRMED);
        booking.setDeparture(departure);
        var address = data.bookingAddress(booking, AddressRole.DELIVERY);
        address.setLatitude(new BigDecimal(latitude));
        address.setLongitude(new BigDecimal(longitude));
        List<Long> horseIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long horse = jdbc.queryForObject("""
                    INSERT INTO horse (owner_customer_id, name, sex, horse_type_id, status)
                    VALUES (?, ?, 'MARE', (SELECT min(id) FROM horse_type), 'ACTIVE') RETURNING id
                    """, Long.class, customer.getUser().getId(), "M4-" + i);
            horseIds.add(jdbc.queryForObject("""
                    INSERT INTO booking_horse (booking_id, horse_id, stall_type_code, status, delivery_address_id)
                    VALUES (?, ?, 'STANDARD', 'ACTIVE', ?) RETURNING id
                    """, Long.class, booking.getId(), horse, address.getId()));
        }
        return horseIds;
    }

    private long allotment(long departure, long vehicleClass, String type) {
        return jdbc.queryForObject("""
                INSERT INTO departure_vehicle (departure_id, vehicle_class_id, vehicle_count, allotment_type, created_at)
                VALUES (?, ?, 1, ?, now()) RETURNING id
                """, Long.class, departure, vehicleClass, type);
    }

    private int trips(Scenario scenario) {
        return jdbc.queryForObject("SELECT count(*) FROM vehicle_trip WHERE departure_id = ?", Integer.class, scenario.departure());
    }

    private List<Long> assigned(long vehicle) {
        return jdbc.queryForList("""
                SELECT l.booking_horse_id FROM horse_leg l JOIN vehicle_trip t ON t.id = l.vehicle_trip_id WHERE t.vehicle_id = ?
                """, Long.class, vehicle);
    }

    private static String body(Scenario sc, List<Long> near) {
        return """
                {"trucks":[{"allotmentId":%d,"number":1,"vehicleId":%d,"bookingHorseIds":%s},
                           {"allotmentId":%d,"number":1,"vehicleId":%d,"bookingHorseIds":%s}]}
                """.formatted(sc.spare(), sc.medium(), sc.far(), sc.base(), sc.heavy(), near);
    }

    private record Scenario(long departure, String token, long base, long spare, long heavy, long medium,
                            List<Long> near, List<Long> far) {
        String path() {
            return "/departures/" + departure + "/linehaul";
        }
    }
}
