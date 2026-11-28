package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.dto.network.DepartureScheduleRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.BookingStatusHistoryRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.repository.RouteRepository;
import com.example.racehorse_transport.support.TestData;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// Chuyến trên DB thật: sinh từ lịch không trùng, tranh khoang cuối, nhả khoang, đóng chuyến, hủy chuyến đổi trạng thái đơn.
@PostgresIntegrationTest
class DepartureServiceTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Autowired private DepartureService departureService;
    @Autowired private BookingStatusService bookingStatus;
    @Autowired private RouteService routeService;
    @Autowired private DepartureRepository departures;
    @Autowired private BookingRepository bookings;
    @Autowired private BookingStatusHistoryRepository history;
    @Autowired private RouteRepository routes;
    @Autowired private TestData testData;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void generatingTwiceDoesNotDuplicateDepartures() {
        DepartureScheduleRequestDto request = new DepartureScheduleRequestDto();
        request.setRouteId(routes.findByCode("HCM-PNH").orElseThrow().getId());
        request.setDepartureTime(LocalTime.of(20, 30));
        request.setBookingCutoffHours(24);
        request.setBaseVehicleClassId(jdbc.queryForObject("SELECT id FROM vehicle_class WHERE code = 'MEDIUM'", Long.class));
        request.setBaseVehicleCount(1);
        request.setValidFrom(TODAY);
        request.setDaysOfWeek(Set.of((short) 1, (short) 2, (short) 3, (short) 4, (short) 5, (short) 6, (short) 7));
        Long departureScheduleId = routeService.createSchedule(request).getId();
        try {
            departureService.generateFromSchedules();
            long generated = departures.findAll().stream()
                    .filter(d -> d.getDepartureSchedule() != null && d.getDepartureSchedule().getId().equals(departureScheduleId)).count();
            // DEPARTURE_GENERATION_WEEKS = 6 trong dữ liệu nền: mỗi ngày một chuyến trong 42 ngày tới
            assertThat(generated).isEqualTo(42);
            assertThat(departureService.generateFromSchedules()).isZero();
        } finally {
            routeService.endSchedule(departureScheduleId);
        }
    }

    @Test
    void onlyOneOfTwoConcurrentReservationsGetsTheLastStall() throws Exception {
        Long departureId = testData.departure("PNH-HCM", TODAY.plusDays(20), 1).getId();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        List<Future<Boolean>> attempts = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) {
                attempts.add(pool.submit(() -> {
                    start.await();
                    try {
                        departureService.reserve(departureId, 1);
                        return true;
                    } catch (IllegalStateException full) {
                        return false;
                    }
                }));
            }
            int succeeded = 0;
            for (Future<Boolean> attempt : attempts) {
                succeeded += attempt.get() ? 1 : 0;
            }
            assertThat(succeeded).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(departures.findById(departureId).orElseThrow().getBookedStallCount()).isEqualTo(1);

        departureService.release(departureId, 1);
        assertThat(departures.findById(departureId).orElseThrow().getBookedStallCount()).isZero();
        assertThatThrownBy(() -> departureService.release(departureId, 1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closesDeparturesPastCutoff() {
        // Chuyến chạy sau 2 ngày, chốt nhận đơn trước 3 ngày: đã qua giờ chốt
        Long departureId = testData.departure("HCM-DN", TODAY.plusDays(2), 9).getId();
        assertThat(departureService.closePastCutoff()).isPositive();
        assertThat(departures.findById(departureId).orElseThrow().getStatus()).isEqualTo(DepartureStatus.CLOSED);
        assertThatThrownBy(() -> departureService.reserve(departureId, 1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancellingDepartureAsksCustomersToChangeSchedule() {
        Departure departure = testData.departure("HCM-PNH", TODAY.plusDays(25), 9);
        Customer customer = testData.customer(testData.unique("khach"));
        Booking confirmed = onDeparture(testData.booking(customer, BookingType.EXPORT, BookingStatus.CONFIRMED), departure);
        Booking cancelled = onDeparture(testData.booking(customer, BookingType.EXPORT, BookingStatus.CANCELLED), departure);
        String manager = testData.unique("quanly");
        testData.staff(manager, "MANAGER");

        departureService.cancel(departure.getId(), "Đóng cửa khẩu", manager);

        assertThat(departures.findById(departure.getId()).orElseThrow().getStatus()).isEqualTo(DepartureStatus.CANCELLED);
        assertThat(bookings.findById(confirmed.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.SCHEDULE_CHANGE_REQUIRED);
        assertThat(bookings.findById(cancelled.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(history.findByBooking_IdOrderByChangedAtAscIdAsc(confirmed.getId())).singleElement().satisfies(row -> {
            assertThat(row.getFromStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(row.getToStatus()).isEqualTo(BookingStatus.SCHEDULE_CHANGE_REQUIRED);
            assertThat(row.getReason()).contains("Đóng cửa khẩu");
        });
        assertThatThrownBy(() -> departureService.cancel(departure.getId(), "Lần hai", manager)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bookingStatusRejectsInvalidTransition() {
        Customer customer = testData.customer(testData.unique("khach"));
        Booking booking = testData.booking(customer, BookingType.LOCAL, BookingStatus.PENDING_INTAKE);
        Long actor = customer.getUser().getId();

        assertThatThrownBy(() -> bookingStatus.transition(booking.getId(), BookingStatus.DELIVERED, null, actor))
                .isInstanceOf(IllegalStateException.class);
        bookingStatus.transition(booking.getId(), BookingStatus.UNDER_REVIEW, null, actor);

        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.UNDER_REVIEW);
        assertThat(history.findByBooking_IdOrderByChangedAtAscIdAsc(booking.getId())).hasSize(1);
    }

    @Test
    void concurrentDeparturesAtOneHubCannotBothTakeItsOnlySpareVehicle() throws Exception {
        Long first = offSchedule("HCM-PNH", 107);
        Long second = offSchedule("HCM-DN", 107);
        try {
            try (var pool = Executors.newFixedThreadPool(2)) {
                var start = new CyclicBarrier(2);
                var attempts = List.of(first, second).stream().map(id -> pool.submit(() -> {
                    start.await();
                    try {
                        departureService.reserve(id, 10);
                        return true;
                    } catch (IllegalStateException full) {
                        return false;
                    }
                })).toList();
                assertThat(List.of(attempts.get(0).get(), attempts.get(1).get())).containsExactlyInAnyOrder(true, false);
            }
            Long reinforced = List.of(first, second).stream()
                    .filter(id -> departures.findById(id).orElseThrow().getBookedStallCount() == 10).findFirst().orElseThrow();
            Long full = first.equals(reinforced) ? second : first;
            assertThat(departureService.detail(full).vehicles()).hasSize(1);
            assertThat(departureService.detail(reinforced).departure().getStallCapacity()).isEqualTo(18);
            var spare = departureService.detail(reinforced).vehicles().stream()
                    .filter(vehicle -> vehicle.allotmentType().name().equals("SPARE")).findFirst().orElseThrow();
            String manager = testData.unique("m2-manager");
            testData.staff(manager, "MANAGER");
            assertThatThrownBy(() -> departureService.removeVehicle(reinforced, spare.id(), "Over capacity", manager))
                    .isInstanceOf(com.example.racehorse_transport.exception.BookingRuleException.class);
            departureService.release(reinforced, 1);
            assertThat(departureService.removeVehicle(reinforced, spare.id(), "No longer needed", manager)
                    .departure().getStallCapacity()).isEqualTo(9);
            departureService.reserve(full, 10);
            assertThat(departureService.detail(full).vehicles()).hasSize(2);
        } finally {
            departures.deleteAllById(List.of(first, second));
        }
    }

    // Chuyến ngoài lịch có một xe nặng làm xe nền
    private Long offSchedule(String routeCode, int days) {
        Long id = testData.departure(routeCode, TODAY.plusDays(days), 9).getId();
        jdbc.update("""
                INSERT INTO departure_vehicle (departure_id, vehicle_class_id, vehicle_count, allotment_type, created_at)
                SELECT ?, id, 1, 'BASE', now() FROM vehicle_class WHERE code = 'HEAVY'
                """, id);
        return id;
    }

    private Booking onDeparture(Booking booking, Departure departure) {
        booking.setDeparture(departure);
        return bookings.saveAndFlush(booking);
    }
}
