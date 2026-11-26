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
            Flyway.configure().dataSource(source)
                    .target("202612012201").load().migrate();
            var jdbc = new JdbcTemplate(source);
            insertConfirmedInsurance(jdbc);
            var existing = jdbc.queryForMap("""
                    SELECT id, booking_horse_id, confirmed_by, confirmed_at FROM horse_insurance
                    WHERE status = 'CONFIRMED' ORDER BY id LIMIT 1
                    """);

            Flyway.configure().dataSource(source).load().migrate();

            assertThat(jdbc.queryForMap("""
                    SELECT id, booking_horse_id, confirmed_by, confirmed_at FROM horse_insurance WHERE id = ?
                    """, existing.get("id"))).isEqualTo(existing);
            assertThat(jdbc.queryForObject("SELECT insurer_name FROM horse_insurance WHERE id = ?",
                    String.class, existing.get("id"))).isEqualTo("Insurer");
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

            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE horse_insurance SET status = 'CONFIRMED', confirmed_by = ?, confirmed_at = ?,
                        coverage_amount = 100, currency_code = 'VND' WHERE id = ?
                    """, existing.get("confirmed_by"), existing.get("confirmed_at"), draft))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_horse_insurance_arranged");
            jdbc.update("""
                    UPDATE horse_insurance SET status = 'CONFIRMED', confirmed_by = ?, confirmed_at = ?,
                        insurer_name = 'Insurer', coverage_amount = 100, currency_code = 'VND' WHERE id = ?
                    """, existing.get("confirmed_by"), existing.get("confirmed_at"), draft);
        }
    }

    private static void insertConfirmedInsurance(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO app_user (username, email, password_hash, is_email_verified, preferred_language, status, role_code)
                VALUES ('insurance.customer', 'insurance.customer@example.test', 'fixture', true, 'vi', 'ACTIVE', 'CUSTOMER'),
                       ('insurance.specialist', 'insurance.specialist@example.test', 'fixture', true, 'vi', 'ACTIVE', 'SPECIALIST')
                """);
        jdbc.update("""
                INSERT INTO location (code, name, location_type, country_code, address, status, is_headquarters)
                VALUES ('INSURANCE-HUB', 'Hub', 'HUB', 'VN', 'Hub', 'ACTIVE', true)
                """);
        jdbc.update("""
                INSERT INTO staff (user_id, staff_code, full_name, phone, hire_date, employment_status, home_hub_location_id)
                SELECT u.id, 'INS-SPEC', 'Specialist', '0900000000', CURRENT_DATE, 'ACTIVE', l.id
                FROM app_user u CROSS JOIN location l WHERE u.username = 'insurance.specialist' AND l.code = 'INSURANCE-HUB'
                """);
        jdbc.update("""
                INSERT INTO customer (user_id, customer_type, full_name, country_code)
                SELECT id, 'PERSON', 'Customer', 'VN' FROM app_user WHERE username = 'insurance.customer'
                """);
        jdbc.update("""
                INSERT INTO party (party_type, display_name, country_code, status)
                VALUES ('ORGANIZATION', 'Insurer', 'VN', 'ACTIVE')
                """);
        jdbc.update("""
                WITH new_horse AS (
                    INSERT INTO horse (owner_customer_id, name, sex, horse_type_id, status)
                    SELECT user_id, 'Test horse', 'MARE', (SELECT min(id) FROM horse_type), 'ACTIVE' FROM customer
                    RETURNING id, owner_customer_id
                ), new_booking AS (
                    INSERT INTO booking (code, customer_id, booking_type, requested_pickup_date, status, created_by)
                    SELECT 'INSURANCE-TEST', owner_customer_id, 'LOCAL', CURRENT_DATE, 'CONFIRMED', owner_customer_id
                    FROM new_horse RETURNING id
                ), new_line AS (
                    INSERT INTO booking_horse (booking_id, horse_id, stall_type_code, status)
                    SELECT b.id, h.id, 'STANDARD', 'ACTIVE' FROM new_booking b CROSS JOIN new_horse h
                    RETURNING id
                )
                INSERT INTO horse_insurance (booking_horse_id, coverage_option, insurer_party_id, coverage_amount,
                                             currency_code, policy_number, valid_from, valid_to, status, confirmed_by, confirmed_at)
                SELECT new_line.id, 'ARRANGED', party.id, 100, 'VND', 'OLD-POLICY', CURRENT_DATE, CURRENT_DATE + 1,
                       'CONFIRMED', staff.user_id, now()
                FROM new_line CROSS JOIN staff CROSS JOIN party
                WHERE staff.staff_code = 'INS-SPEC' AND party.display_name = 'Insurer'
                """);
    }
}
