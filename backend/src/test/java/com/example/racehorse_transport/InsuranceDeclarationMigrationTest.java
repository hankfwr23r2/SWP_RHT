package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class InsuranceDeclarationMigrationTest {

    @Test
    void upgradesExistingDataAndRequiresInsurerAndCoverageOnlyOnConfirmation() {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).locations("classpath:db/migration", "classpath:db/seed-dev")
                    .target("202611231600").load().migrate();
            var jdbc = new JdbcTemplate(source);
            var existing = jdbc.queryForMap("""
                    SELECT id, booking_horse_id, confirmed_by, confirmed_at FROM horse_insurance
                    WHERE status = 'CONFIRMED' ORDER BY id LIMIT 1
                    """);

            Flyway.configure().dataSource(source).locations("classpath:db/migration", "classpath:db/seed-dev")
                    .load().migrate();

            assertThat(jdbc.queryForMap("""
                    SELECT id, booking_horse_id, confirmed_by, confirmed_at FROM horse_insurance WHERE id = ?
                    """, existing.get("id"))).isEqualTo(existing);
            jdbc.update("DELETE FROM horse_insurance WHERE id = ?", existing.get("id"));
            Long draft = jdbc.queryForObject("""
                    INSERT INTO horse_insurance (booking_horse_id, coverage_option, status)
                    VALUES (?, 'ARRANGED', 'DRAFT') RETURNING id
                    """, Long.class, existing.get("booking_horse_id"));
            assertThat(draft).isNotNull();

            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE horse_insurance SET status = 'CONFIRMED', confirmed_by = ?, confirmed_at = ?,
                        coverage_amount = 100, currency_code = 'VND' WHERE id = ?
                    """, existing.get("confirmed_by"), existing.get("confirmed_at"), draft))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_horse_insurance_arranged");

            Long partyId = jdbc.queryForObject("SELECT min(id) FROM party", Long.class);
            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE horse_insurance SET status = 'CONFIRMED', confirmed_by = ?, confirmed_at = ?,
                        insurer_party_id = ? WHERE id = ?
                    """, existing.get("confirmed_by"), existing.get("confirmed_at"), partyId, draft))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_horse_insurance_coverage");
        }
    }
}
