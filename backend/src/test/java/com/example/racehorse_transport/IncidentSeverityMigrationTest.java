package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class IncidentSeverityMigrationTest {

    @Test
    void backfillsEachLegacyIncidentTypeAndEnforcesSeverityAndUnlockConstraints() {
        try (var postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).locations("classpath:db/migration", "classpath:db/seed-dev")
                    .target("202611291200").load().migrate();
            var jdbc = new JdbcTemplate(source);
            assertThat(jdbc.update("""
                    INSERT INTO incident (booking_id, incident_type, cause_type, title, description, reported_by, status)
                    SELECT b.id, types.type, CASE WHEN types.type <> 'COMPLAINT' THEN 'COMPANY_FAULT' END,
                        'Legacy severity fixture', 'Before V202611291600',
                        CASE WHEN types.type <> 'COMPLAINT' THEN (SELECT min(user_id) FROM staff) END,
                        'REPORTED'
                    FROM booking b CROSS JOIN (VALUES ('HEALTH'), ('ACCIDENT'), ('VEHICLE_BREAKDOWN'), ('HANDOVER_TIMEOUT'),
                        ('DOCUMENT'), ('DELAY'), ('OTHER'), ('COMPLAINT')) AS types(type)
                    WHERE b.code = 'DEV-DIRECT-001'
                    """)).isEqualTo(8);

            Flyway.configure().dataSource(source).locations("classpath:db/migration", "classpath:db/seed-dev")
                    .target("202611291600").load().migrate();

            assertThat(jdbc.queryForList("""
                    SELECT incident_type || ':' || severity
                    FROM incident WHERE title = 'Legacy severity fixture'
                    """, String.class)).containsExactlyInAnyOrder(
                    "HEALTH:HIGH", "ACCIDENT:HIGH", "VEHICLE_BREAKDOWN:MEDIUM", "HANDOVER_TIMEOUT:MEDIUM",
                    "DOCUMENT:MEDIUM", "DELAY:LOW", "OTHER:LOW", "COMPLAINT:LOW");
            long id = jdbc.queryForObject("""
                    SELECT id FROM incident WHERE incident_type = 'HEALTH' AND title = 'Legacy severity fixture'
                    """, Long.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE incident SET severity = 'CRITICAL' WHERE id = ?", id))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_incident_severity");
            assertThatThrownBy(() -> jdbc.update("UPDATE incident SET severity = NULL WHERE id = ?", id))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("severity");
            assertThatThrownBy(() -> jdbc.update("UPDATE incident SET trip_unlocked_at = now() WHERE id = ?", id))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_incident_trip_unlock");
        }
    }
}
