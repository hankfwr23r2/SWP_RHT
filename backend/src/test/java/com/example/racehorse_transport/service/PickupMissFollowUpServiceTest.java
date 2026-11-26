package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.policy.PickupMissPolicy;
import com.example.racehorse_transport.service.DepartureDecisionServiceTest.BookingFixture;
import com.example.racehorse_transport.support.TestData;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.sql.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresIntegrationTest
class PickupMissFollowUpServiceTest {
    @Autowired private PickupMissFollowUpService followUps;
    @Autowired private BookingService bookings;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    @Autowired private TestData data;

    private Long booking;

    @BeforeEach
    void missedBooking() {
        var fixture = new BookingFixture(bookings, data, jdbc, clock);
        LocalDate date = fixture.day(33);
        Long departure = data.departure("HCM-DN", date, 9).getId();
        booking = fixture.submit(fixture.customer(), departure, date, "STANDARD");
        jdbc.update("update booking set status = 'SCHEDULE_CHANGE_REQUIRED', departure_id = null where id = ?", booking);
        jdbc.update("update booking_horse set pickup_missed_at = now() where booking_id = ?", booking);
    }

    private void waiting(int days, String reason) {
        jdbc.update("delete from booking_status_history where booking_id = ?", booking);
        jdbc.update("""
                insert into booking_status_history (booking_id, from_status, to_status, reason, changed_at)
                values (?, 'CONFIRMED', 'SCHEDULE_CHANGE_REQUIRED', ?, ?)
                """, booking, reason, Timestamp.from(clock.instant().minus(Duration.ofDays(days)).minusSeconds(5)));
    }

    private int notices(String title) {
        return jdbc.queryForObject("select count(*) from notification where booking_id = ? and title like ?",
                Integer.class, booking, title);
    }

    private String status() {
        return jdbc.queryForObject("select status from booking where id = ?", String.class, booking);
    }

    @Test
    void remindsOnceForEachMilestone() {
        waiting(2, PickupMissPolicy.RELEASED_REASON);
        assertThat(followUps.candidates()).doesNotContain(booking);
        waiting(3, PickupMissPolicy.RELEASED_REASON);
        assertThat(followUps.candidates()).contains(booking);
        followUps.process(booking);
        followUps.process(booking);
        assertThat(notices("Nhắc chọn lại lịch%")).isEqualTo(1);
        assertThat(status()).isEqualTo("SCHEDULE_CHANGE_REQUIRED");
    }

    @Test
    void ignoresOrdersWaitingForAnotherReason() {
        waiting(40, "Chuyến bị hủy");
        assertThat(followUps.candidates()).doesNotContain(booking);
        followUps.process(booking);
        assertThat(status()).isEqualTo("SCHEDULE_CHANGE_REQUIRED");
    }

    @Test
    void expiredWindowWaitsForLiabilityThenCancelsOnce() {
        data.staff(data.unique("quanly"), "MANAGER");
        waiting(30, PickupMissPolicy.RELEASED_REASON);
        followUps.process(booking);
        followUps.process(booking);
        assertThat(status()).isEqualTo("SCHEDULE_CHANGE_REQUIRED");
        assertThat(notices("Cần chốt bên chịu%")).isEqualTo(1);

        jdbc.update("update booking_horse set pickup_miss_liability = 'COMPANY' where booking_id = ?", booking);
        followUps.process(booking);
        followUps.process(booking);
        assertThat(status()).isEqualTo("CANCELLED");
        assertThat(notices("Đơn đã hủy vì hết hạn%")).isEqualTo(1);
    }
}
