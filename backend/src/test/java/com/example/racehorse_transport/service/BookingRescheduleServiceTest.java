package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.dto.booking.BookingRescheduleRequestDto;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.service.DepartureDecisionServiceTest.BookingFixture;
import com.example.racehorse_transport.support.TestData;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresIntegrationTest
class BookingRescheduleServiceTest {

    @Autowired private BookingRescheduleService reschedules;
    @Autowired private DepartureService departures;
    @Autowired private BookingService bookings;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    @Autowired private TestData data;

    private BookingFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new BookingFixture(bookings, data, jdbc, clock);
    }

    @Test
    void customerReschedulesUnpaidBooking() {
        LocalDate date = fixture.day(31);
        Long oldId = data.departure("HCM-DN", date, 9).getId();
        Long newId = data.departure("HCM-DN", date.plusDays(7), 9).getId();
        String owner = fixture.customer();
        Long booking = fixture.submit(owner, oldId, date, "WIDE");
        var request = new BookingRescheduleRequestDto(newId, date.plusDays(6));
        assertThatThrownBy(() -> reschedules.reschedule(booking, owner, request)).isInstanceOf(BookingRuleException.class);
        fixture.setStatus(booking, "SCHEDULE_CHANGE_REQUIRED");
        assertThatThrownBy(() -> reschedules.reschedule(booking, fixture.customer(), request))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(reschedules.reschedule(booking, owner, request).status()).isEqualTo(BookingStatus.UNDER_REVIEW);

        assertThat(fixture.reserved(oldId)).isZero();
        assertThat(fixture.reserved(newId)).isEqualTo(2);
    }

    @Test
    void reschedulesAfterDepartureWasCancelledAndAfterSeatsWereReleased() {
        LocalDate date = fixture.day(38);
        Long cancelled = data.departure("HCM-DN", date, 9).getId();
        Long released = data.departure("HCM-DN", date, 9).getId();
        Long next = data.departure("HCM-DN", date.plusDays(7), 9).getId();
        String owner = fixture.customer();
        Long first = fixture.submit(owner, cancelled, date, "STANDARD");
        Long second = fixture.submit(owner, released, date, "WIDE");
        departures.cancel(cancelled, "Đóng cửa khẩu", fixture.staff("MANAGER"));
        fixture.departsIn(released, 20);
        assertThat(reschedules.releaseAtDecision(second)).isTrue();

        reschedules.reschedule(first, owner, new BookingRescheduleRequestDto(next, date.plusDays(6)));
        reschedules.reschedule(second, owner, new BookingRescheduleRequestDto(next, date.plusDays(6)));

        assertThat(fixture.reserved(cancelled)).isZero();
        assertThat(fixture.reserved(released)).isZero();
        assertThat(fixture.reserved(next)).isEqualTo(3);
    }

    @Test
    void paidBookingAndFullDepartureAreRejectedWithoutChanges() {
        LocalDate date = fixture.day(31);
        Long oldId = data.departure("HCM-DN", date, 9).getId();
        Long fullId = data.departure("HCM-DN", date.plusDays(7), 1).getId();
        String owner = fixture.customer();
        Long booking = fixture.submit(owner, oldId, date, "WIDE");
        fixture.setStatus(booking, "SCHEDULE_CHANGE_REQUIRED");
        var request = new BookingRescheduleRequestDto(fullId, date.plusDays(6));
        assertThatThrownBy(() -> reschedules.reschedule(booking, owner, request)).isInstanceOf(IllegalStateException.class);
        fixture.setWaybill(booking);
        assertThatThrownBy(() -> reschedules.reschedule(booking, owner, request)).isInstanceOf(BookingRuleException.class)
                .hasMessageContaining("Thiếu lịch sử chuyến vừa lỡ");

        assertThat(fixture.reserved(oldId)).isEqualTo(2);
        assertThat(fixture.reserved(fullId)).isZero();
        assertThat(fixture.statusOf(booking)).isEqualTo("SCHEDULE_CHANGE_REQUIRED");
    }

    @Test
    void paidBookingCanMoveOnceForCustomerAndKeepsCompanyAndAuthorityChangesFree() {
        LocalDate date = fixture.day(31);
        Long original = data.departure("HCM-DN", date, 9).getId();
        Long replacement = data.departure("HCM-DN", date.plusDays(7), 9).getId();
        Long tooLate = data.departure("HCM-DN", date.plusDays(31), 9).getId();
        String owner = fixture.customer();
        Long booking = fixture.submit(owner, original, date, "WIDE");
        fixture.setStatus(booking, "SCHEDULE_CHANGE_REQUIRED");
        fixture.setWaybill(booking);
        recordMissedDeparture(booking, original, "AUTHORITY");
        recordMissedDeparture(booking, original, "CUSTOMER");

        assertThatThrownBy(() -> reschedules.reschedule(booking, owner, new BookingRescheduleRequestDto(tooLate, date.plusDays(30))))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("ngoài hạn giữ giá");
        assertThat(reschedules.reschedule(booking, owner, new BookingRescheduleRequestDto(replacement, date.plusDays(6))).status())
                .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(fixture.reserved(original)).isZero();
        assertThat(fixture.reserved(replacement)).isEqualTo(2);

        fixture.setStatus(booking, "SCHEDULE_CHANGE_REQUIRED");
        recordMissedDeparture(booking, replacement, "CUSTOMER");
        var second = new BookingRescheduleRequestDto(tooLate, date.plusDays(30));
        assertThatThrownBy(() -> reschedules.reschedule(booking, owner, second))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("đã dùng lượt");
        assertThat(fixture.reserved(replacement)).isEqualTo(2);

        recordMissedDeparture(booking, replacement, "COMPANY");
        assertThat(reschedules.reschedule(booking, owner, second).status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(fixture.reserved(replacement)).isZero();
        assertThat(fixture.reserved(tooLate)).isEqualTo(2);
    }

    private void recordMissedDeparture(Long booking, Long departure, String cause) {
        jdbc.update("""
                insert into booking_status_history (booking_id, from_status, to_status, previous_departure_id, cause_type)
                values (?, 'CONFIRMED', 'SCHEDULE_CHANGE_REQUIRED', ?, ?)
                """, booking, departure, cause);
    }

    @Test
    void swappingTwoDeparturesTogetherDoesNotDeadlock() throws Exception {
        LocalDate date = fixture.day(35);
        Long a = data.departure("HCM-DN", date, 9).getId();
        Long b = data.departure("HCM-DN", date.plusDays(1), 9).getId();
        String ownerA = fixture.customer();
        String ownerB = fixture.customer();
        Long onA = fixture.submit(ownerA, a, date, "STANDARD");
        Long onB = fixture.submit(ownerB, b, date.plusDays(1), "WIDE");
        fixture.setStatus(onA, "SCHEDULE_CHANGE_REQUIRED");
        fixture.setStatus(onB, "SCHEDULE_CHANGE_REQUIRED");

        BookingFixture.race(() -> reschedules.reschedule(onA, ownerA, new BookingRescheduleRequestDto(b, date)),
                () -> reschedules.reschedule(onB, ownerB, new BookingRescheduleRequestDto(a, date.minusDays(1))));

        assertThat(fixture.reserved(a)).isEqualTo(2);
        assertThat(fixture.reserved(b)).isEqualTo(1);
    }

    @Test
    void cancellingAndReschedulingTogetherKeepSeatsConsistent() throws Exception {
        LocalDate date = fixture.day(36);
        Long a = data.departure("HCM-DN", date, 9).getId();
        Long b = data.departure("HCM-DN", date.plusDays(1), 9).getId();
        String owner = fixture.customer();
        Long booking = fixture.submit(owner, a, date, "STANDARD");
        fixture.setStatus(booking, "SCHEDULE_CHANGE_REQUIRED");
        String manager = fixture.staff("MANAGER");

        BookingFixture.race(() -> departures.cancel(a, "Không đủ ngựa", manager),
                () -> reschedules.reschedule(booking, owner, new BookingRescheduleRequestDto(b, date)));

        assertThat(fixture.departureStatus(a)).isEqualTo("CANCELLED");
        assertThat(fixture.reserved(a)).isZero();
        assertThat(fixture.reserved(b)).isEqualTo(1);
        assertThat(fixture.departureOf(booking)).isEqualTo(b);
    }
}
