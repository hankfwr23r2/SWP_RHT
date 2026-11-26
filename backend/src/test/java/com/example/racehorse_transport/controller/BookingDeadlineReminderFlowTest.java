package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.scheduler.BookingDeadlineReminderScheduler;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.BookingDeadlineReminderService;
import com.example.racehorse_transport.support.TestData;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.booking.deadline-reminder-cron=-")
@Transactional
class BookingDeadlineReminderFlowTest {
    private static final Instant SCAN = Instant.parse("2026-11-28T16:59:00Z");
    private static final Instant NEXT_DAY = SCAN.plusSeconds(60);
    private static final String INTAKE = "Đơn quá hạn tiếp nhận";
    private static final String REVIEW = "Đơn quá hạn thẩm định";
    private static final String QUOTE = "Đơn quá hạn gửi báo giá";

    @Autowired private BookingDeadlineReminderScheduler scheduler;
    @Autowired private BookingDeadlineReminderService reminders;
    @Autowired private TestData data;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc mvc;
    @Autowired private JwtUtils jwt;
    @MockitoBean private Clock clock;

    private Customer customer;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(SCAN);
        customer = data.customer(data.unique("deadline.customer"));
    }

    @Test
    void remindsTheRightStaffOnceEachDayWithoutMovingBookings() throws Exception {
        Staff specialist = data.staff(data.unique("deadline.specialist"), "SPECIALIST");
        Staff offDuty = data.staff(data.unique("deadline.manager"), "MANAGER");
        offDuty.setEmploymentStatus(EmploymentStatus.ON_LEAVE);
        Booking intake = booking(BookingStatus.PENDING_INTAKE, SCAN.minus(Duration.ofHours(4)));
        Booking review = booking(BookingStatus.UNDER_REVIEW, SCAN.minus(Duration.ofDays(3)));
        Booking quote = booking(BookingStatus.QUOTING, SCAN.minus(Duration.ofDays(3)));
        review.setSpecialist(specialist);
        entry(review, BookingStatus.PENDING_INTAKE, BookingStatus.UNDER_REVIEW, SCAN.minus(Duration.ofHours(24)));
        entry(quote, BookingStatus.UNDER_REVIEW, BookingStatus.QUOTING, SCAN.minus(Duration.ofHours(24)));
        entityManager.flush();
        entityManager.clear();

        scheduler.sendReminders();
        scheduler.sendReminders();

        mine("manager.dev", "MANAGER")
                .andExpect(jsonPath("$.data[*].title").value(hasItems(INTAKE, QUOTE)))
                .andExpect(jsonPath("$.data[*].title").value(not(hasItems(REVIEW))));
        mine(specialist.getUser().getUsername(), "SPECIALIST")
                .andExpect(jsonPath("$.data[0].title").value(REVIEW))
                .andExpect(jsonPath("$.data[0].message").value(review.getCode()))
                .andExpect(jsonPath("$.data[0].linkUrl").value("/specialist/verification/" + review.getId()));
        mine("specialist.dev", "SPECIALIST")
                .andExpect(jsonPath("$.data[*].title").value(not(hasItems(REVIEW))));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_recipient WHERE user_id = ?",
                Integer.class, offDuty.getUserId())).isZero();
        assertThat(noticeCount(intake, INTAKE)).isEqualTo(1);
        assertThat(noticeCount(review, REVIEW)).isEqualTo(1);
        assertThat(noticeCount(quote, QUOTE)).isEqualTo(1);

        when(clock.instant()).thenReturn(NEXT_DAY);
        scheduler.sendReminders();
        scheduler.sendReminders();
        assertThat(noticeCount(intake, INTAKE)).isEqualTo(2);
        assertThat(noticeCount(review, REVIEW)).isEqualTo(2);
        assertThat(noticeCount(quote, QUOTE)).isEqualTo(2);
        assertThat(bookingStatus(intake)).isEqualTo("PENDING_INTAKE");
        assertThat(bookingStatus(review)).isEqualTo("UNDER_REVIEW");
        assertThat(bookingStatus(quote)).isEqualTo("QUOTING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_status_history WHERE booking_id IN (?, ?, ?)",
                Integer.class, intake.getId(), review.getId(), quote.getId())).isEqualTo(2);
    }

    @Test
    void waitsForTheLatestEntryAndRechecksProgressBeforeSending() {
        jdbc.update("UPDATE system_config SET config_value = '2' WHERE config_key = 'BOOKING_REVIEW_DUE_HOURS'");
        Staff specialist = data.staff(data.unique("deadline.reviewer"), "SPECIALIST");
        Instant old = SCAN.minus(Duration.ofDays(3));
        Booking review = booking(BookingStatus.UNDER_REVIEW, old);
        review.setSpecialist(specialist);
        entry(review, BookingStatus.PENDING_INTAKE, BookingStatus.UNDER_REVIEW, old);
        entry(review, BookingStatus.UNDER_REVIEW, BookingStatus.NEEDS_RESUBMISSION, old.plusSeconds(60));
        entry(review, BookingStatus.NEEDS_RESUBMISSION, BookingStatus.UNDER_REVIEW, SCAN.minus(Duration.ofHours(1)));
        Booking requote = booking(BookingStatus.QUOTING, old);
        entry(requote, BookingStatus.UNDER_REVIEW, BookingStatus.QUOTING, old);
        entry(requote, BookingStatus.QUOTING, BookingStatus.AWAITING_DEPOSIT, old.plusSeconds(60));
        entry(requote, BookingStatus.AWAITING_DEPOSIT, BookingStatus.QUOTING, SCAN.minus(Duration.ofHours(1)));
        Booking waitingForCustomer = booking(BookingStatus.NEEDS_RESUBMISSION, old);
        entry(waitingForCustomer, BookingStatus.PENDING_INTAKE, BookingStatus.UNDER_REVIEW, old);
        entry(waitingForCustomer, BookingStatus.UNDER_REVIEW, BookingStatus.NEEDS_RESUBMISSION, old.plusSeconds(60));
        Booking unassigned = booking(BookingStatus.UNDER_REVIEW, old);
        entry(unassigned, BookingStatus.PENDING_INTAKE, BookingStatus.UNDER_REVIEW, old);
        Booking progressed = booking(BookingStatus.PENDING_INTAKE, old);
        entityManager.flush();
        entityManager.clear();

        assertThat(reminders.overdueBookingIds()).contains(progressed.getId(), unassigned.getId())
                .doesNotContain(review.getId(), requote.getId(), waitingForCustomer.getId());
        jdbc.update("UPDATE booking SET status = 'REJECTED' WHERE id = ?", progressed.getId());
        reminders.remind(progressed.getId());
        scheduler.sendReminders();
        assertThat(noticeCount(progressed, INTAKE)).isZero();
        assertThat(noticeCount(unassigned, REVIEW)).isZero();
        assertThat(noticeCount(review, REVIEW)).isZero();
        assertThat(noticeCount(requote, QUOTE)).isZero();

        when(clock.instant()).thenReturn(SCAN.plus(Duration.ofHours(1)));
        scheduler.sendReminders();
        assertThat(noticeCount(review, REVIEW)).isEqualTo(1);
        assertThat(noticeCount(waitingForCustomer, REVIEW)).isZero();
        assertThat(noticeCount(requote, QUOTE)).isZero();
    }

    private Booking booking(BookingStatus state, Instant createdAt) {
        Booking booking = data.booking(customer, BookingType.LOCAL, state);
        jdbc.update("UPDATE booking SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), booking.getId());
        return booking;
    }

    private void entry(Booking booking, BookingStatus from, BookingStatus to, Instant at) {
        jdbc.update("""
                INSERT INTO booking_status_history (booking_id, from_status, to_status, changed_by, changed_at)
                VALUES (?, ?, ?, ?, ?)
                """, booking.getId(), from.name(), to.name(), customer.getUserId(), Timestamp.from(at));
    }

    private int noticeCount(Booking booking, String title) {
        return jdbc.queryForObject("SELECT count(*) FROM notification WHERE booking_id = ? AND title = ?",
                Integer.class, booking.getId(), title);
    }

    private String bookingStatus(Booking booking) {
        return jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, booking.getId());
    }

    private ResultActions mine(String username, String role) throws Exception {
        return mvc.perform(get("/notifications/mine").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + jwt.generateToken(username, List.of(role)))).andExpect(status().isOk());
    }
}
