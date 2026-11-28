package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddressInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.HorseInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.PartyInput;
import com.example.racehorse_transport.dto.booking.InsuranceDeclarationRequestDto;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingPartyRole;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.InsuranceCoverageOption;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@PostgresIntegrationTest
@AutoConfigureMockMvc
class DepartureDecisionServiceTest {

    @Autowired private DepartureDecisionService decisions;
    @Autowired private BookingRescheduleService reschedules;
    @Autowired private DepartureService departures;
    @Autowired private BookingService bookings;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;
    @Autowired private MockMvc mvc;

    private BookingFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new BookingFixture(bookings, data, jdbc, clock);
    }

    @Test
    void confirmsRunOnlyInsideDecisionWindowAndOnlyByManager() throws Exception {
        Long id = data.departure("HCM-DN", fixture.day(30), 9).getId();
        String manager = fixture.staff("MANAGER");
        assertThatThrownBy(() -> decisions.confirmRun(id, manager)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mốc quyết định");
        fixture.departsIn(id, 20);
        mvc.perform(post("/departures/{id}/confirm-run", id)
                        .header(HttpHeaders.AUTHORIZATION, token(fixture.staff("COORDINATOR"), "COORDINATOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/departures/decisions").header(HttpHeaders.AUTHORIZATION, token(fixture.customer(), "CUSTOMER"))
                        .param("from", fixture.day(0).toString()).param("to", fixture.day(1).toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/departures/{id}/confirm-run", id).header(HttpHeaders.AUTHORIZATION, token(manager, "MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.belowMinimum").value(true))
                .andExpect(jsonPath("$.data.confirmable").value(false))
                .andExpect(jsonPath("$.data.runConfirmedAt").exists());
        assertThatThrownBy(() -> decisions.confirmRun(id, manager)).hasMessageContaining("đã được xác nhận");
    }

    @Test
    void boardCountsSeatsOfPaidBookingsByStall() {
        LocalDate date = fixture.day(33);
        Long id = data.departure("HCM-DN", date, 9).getId();
        Long paid = fixture.submit(fixture.customer(), id, date, "WIDE");
        fixture.submit(fixture.customer(), id, date, "STANDARD");
        fixture.setStatus(paid, "IN_OPERATION");

        var row = decisions.decisions(date, date).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();

        assertThat(row.committedStallCount()).isEqualTo(2);
        assertThat(row.bookedStallCount()).isEqualTo(3);
        assertThat(row.uncommittedBookingCount()).isEqualTo(1);
        assertThat(row.belowMinimum()).isTrue();
        assertThat(row.confirmable()).isFalse();
    }

    @Test
    void releasesOnlyBookingsWithoutQuoteAndIsIdempotent() {
        LocalDate date = fixture.day(31);
        Long id = data.departure("HCM-DN", date, 9).getId();
        Long pending = fixture.submit(fixture.customer(), id, date, "WIDE");
        Long quoted = fixture.submit(fixture.customer(), id, date, "STANDARD");
        fixture.setStatus(quoted, "AWAITING_DEPOSIT");
        assertThat(reschedules.releaseAtDecision(pending)).isFalse();
        fixture.departsIn(id, 20);

        assertThat(decisions.releaseUnconfirmed()).isGreaterThanOrEqualTo(1);
        assertThat(reschedules.releaseAtDecision(pending)).isFalse();

        assertThat(fixture.statusOf(pending)).isEqualTo("SCHEDULE_CHANGE_REQUIRED");
        assertThat(fixture.statusOf(quoted)).isEqualTo("AWAITING_DEPOSIT");
        assertThat(fixture.reserved(id)).isEqualTo(1);
        assertThat(fixture.departureOf(pending)).isNull();
        assertThat(fixture.systemHistoryRows(pending)).isEqualTo(1);
    }

    @Test
    void cancellationIsBlockedOnceAHorseWasReceived() {
        LocalDate date = fixture.day(32);
        Long id = data.departure("HCM-DN", date, 9).getId();
        Long received = fixture.submit(fixture.customer(), id, date, "STANDARD");
        Long waiting = fixture.submit(fixture.customer(), id, date, "STANDARD");
        fixture.setStatus(received, "IN_OPERATION");
        String manager = fixture.staff("MANAGER");

        assertThatThrownBy(() -> departures.cancel(id, "Hỏng xe", manager)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Xử lý bằng sự cố");

        assertThat(fixture.statusOf(waiting)).isEqualTo("PENDING_INTAKE");
        assertThat(fixture.departureStatus(id)).isEqualTo("OPEN");
    }

    @Test
    void cancellingAndReleasingTogetherDoNotDeadlock() throws Exception {
        LocalDate date = fixture.day(34);
        Long id = data.departure("HCM-DN", date, 9).getId();
        Long first = fixture.submit(fixture.customer(), id, date, "STANDARD");
        Long second = fixture.submit(fixture.customer(), id, date, "STANDARD");
        fixture.departsIn(id, 20);
        String manager = fixture.staff("MANAGER");

        BookingFixture.race(() -> departures.cancel(id, "Không đủ ngựa", manager), () -> reschedules.releaseAtDecision(first));

        assertThat(fixture.statusOf(first)).isEqualTo("SCHEDULE_CHANGE_REQUIRED");
        assertThat(fixture.statusOf(second)).isEqualTo("SCHEDULE_CHANGE_REQUIRED");
    }

    private String token(String username, String role) {
        return "Bearer " + jwt.generateToken(username, List.of(role), 0);
    }

    // Dữ liệu đơn và chuyến cho test của quyết định chạy và đổi chuyến
    record BookingFixture(BookingService bookings, TestData data, JdbcTemplate jdbc, Clock clock) {

        static void race(Callable<?> one, Callable<?> two) throws Exception {
            CountDownLatch start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return one.call();
                });
                var second = executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return two.call();
                });
                start.countDown();
                first.get(20, TimeUnit.SECONDS);
                second.get(20, TimeUnit.SECONDS);
            }
        }

        // Đón trước ngày chạy một ngày
        Long submit(String owner, Long departureId, LocalDate departureDate, String stallType) {
            var parties = Arrays.stream(BookingPartyRole.values())
                    .map(role -> new PartyInput(role, role.name(), "0901234567", null, null, null, "Test address")).toList();
            var horse = new HorseInput(horse(owner), stallType, null, null, false, List.of(),
                    new InsuranceDeclarationRequestDto(InsuranceCoverageOption.NONE, null, null, null, null, true));
            return bookings.submit(owner, null, new BookingCreateRequestDto(BookingType.INTERPROVINCE, departureId,
                    departureDate.minusDays(1), new BigDecimal("1000"), parties,
                    List.of(address(AddressRole.PICKUP, "VN-79"), address(AddressRole.DELIVERY, "VN-48")), List.of(horse), true)).id();
        }

        private AddressInput address(AddressRole role, String province) {
            return new AddressInput(role, "Test address", province, "VN", new BigDecimal("10.8"), new BigDecimal("106.8"),
                    "Test contact", "0901234567", null, null);
        }

        private Long horse(String owner) {
            return jdbc.queryForObject("""
                    INSERT INTO horse (owner_customer_id, name, sex, horse_type_id, status)
                    SELECT u.id, ?, 'MARE', (SELECT min(id) FROM horse_type), 'ACTIVE' FROM app_user u WHERE u.username = ?
                    RETURNING id
                    """, Long.class, data.unique("pr3-horse"), owner);
        }

        String customer() {
            String username = data.unique("pr3-customer");
            data.customer(username);
            return username;
        }

        String staff(String role) {
            String username = data.unique("pr3-staff");
            data.staff(username, role);
            return username;
        }

        void departsIn(Long departureId, int hours) {
            jdbc.update("""
                    UPDATE departure SET planned_departure_at = now() + make_interval(hours => ?),
                    booking_cutoff_at = now() - interval '1 hour', status = 'CLOSED' WHERE id = ?
                    """, hours, departureId);
        }

        void setStatus(Long bookingId, String status) {
            jdbc.update("UPDATE booking SET status = ? WHERE id = ?", status, bookingId);
        }

        void setWaybill(Long bookingId) {
            jdbc.update("UPDATE booking SET waybill_number = ? WHERE id = ?", data.unique("WB"), bookingId);
        }

        String statusOf(Long bookingId) {
            return jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, bookingId);
        }

        Long departureOf(Long bookingId) {
            return jdbc.queryForObject("SELECT departure_id FROM booking WHERE id = ?", Long.class, bookingId);
        }

        // changed_by NULL = hệ thống
        int systemHistoryRows(Long bookingId) {
            return jdbc.queryForObject("SELECT count(*) FROM booking_status_history WHERE booking_id = ? AND changed_by IS NULL",
                    Integer.class, bookingId);
        }

        String departureStatus(Long id) {
            return jdbc.queryForObject("SELECT status FROM departure WHERE id = ?", String.class, id);
        }

        int reserved(Long id) {
            return jdbc.queryForObject("SELECT booked_stall_count FROM departure WHERE id = ?", Integer.class, id);
        }

        LocalDate day(int plus) {
            return LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(plus);
        }
    }
}
