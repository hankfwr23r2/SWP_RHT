package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@Transactional
class ComplaintIncidentMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void bookingConfirmationIsOptionalAndCanBeRecorded() {
        Long bookingId = newBooking();
        String selectConfirmation = "SELECT delivery_confirmed_at FROM booking WHERE id = ?";
        assertThat(jdbc.queryForObject(selectConfirmation, OffsetDateTime.class, bookingId)).isNull();

        OffsetDateTime confirmedAt = OffsetDateTime.parse("2026-11-25T08:00:00Z");
        assertThat(jdbc.update("UPDATE booking SET delivery_confirmed_at = ? WHERE id = ?", confirmedAt, bookingId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(selectConfirmation, OffsetDateTime.class, bookingId)).isEqualTo(confirmedAt);
    }

    @Test
    void allowsBookingComplaintWithoutStaffReporterOrCause() {
        Long bookingId = newBooking();

        int inserted = insertIncident(bookingId, "COMPLAINT", null, null);

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    void databaseRejectsSecondOpenComplaintForTheSameBooking() {
        Long bookingId = newBooking();
        insertIncident(bookingId, "COMPLAINT", null, null);

        assertThatThrownBy(() -> insertIncident(bookingId, "COMPLAINT", null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_incident_open_complaint_booking");
    }

    @Test
    void allowsAnotherComplaintAfterThePreviousOneIsClosed() {
        Long closedBooking = newBooking();
        insertIncident(closedBooking, "COMPLAINT", null, null);
        jdbc.update("UPDATE incident SET status = 'CLOSED' WHERE booking_id = ?", closedBooking);
        assertThat(insertIncident(closedBooking, "COMPLAINT", null, null)).isEqualTo(1);
    }

    @Test
    void requiresBookingForComplaint() {
        assertThatThrownBy(() -> insertIncident(null, "COMPLAINT", null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_incident_complaint_booking");
    }

    @Test
    void requiresStaffReporterForOperationalIncident() {
        Long bookingId = newBooking();

        assertThatThrownBy(() -> insertIncident(bookingId, "OTHER", null, "COMPANY_FAULT"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_incident_operational_fields");
    }

    @Test
    void requiresCauseForOperationalIncident() {
        Long bookingId = newBooking();
        Long staffId = jdbc.queryForObject("SELECT MIN(user_id) FROM staff", Long.class);
        assertThat(staffId).isNotNull();

        assertThatThrownBy(() -> insertIncident(bookingId, "OTHER", staffId, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_incident_operational_fields");
    }

    private Long newBooking() {
        return jdbc.queryForObject("""
                INSERT INTO booking (code, customer_id, booking_type, requested_pickup_date, status, created_by)
                SELECT 'TEST-G1-MIGRATION', c.user_id, 'LOCAL', CURRENT_DATE, 'DELIVERED', c.user_id
                FROM customer c JOIN app_user u ON u.id = c.user_id
                WHERE u.username = 'customer1.dev'
                RETURNING id
                """, Long.class);
    }

    private int insertIncident(Long bookingId, String incidentType, Long reportedBy, String causeType) {
        return jdbc.update("""
                INSERT INTO incident (booking_id, incident_type, severity, reported_by, cause_type, title, description, status)
                VALUES (?, ?, 'LOW', ?, ?, 'Migration test', 'Test fixture', 'REPORTED')
                """, bookingId, incidentType, reportedBy, causeType);
    }
}
