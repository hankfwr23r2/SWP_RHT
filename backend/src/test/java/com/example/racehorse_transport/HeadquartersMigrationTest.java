package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class HeadquartersMigrationTest {

    @Test
    void infersHeadquartersAndEnforcesTypeAndUniqueness() {
        try (var postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            var source = source(postgres);
            migrateBefore(source);
            var jdbc = new JdbcTemplate(source);
            long headquarters = location(jdbc, "HQ", "HUB");
            long satellite = location(jdbc, "SATELLITE", "HUB");
            long rest = location(jdbc, "REST", "REST_STOP");
            vehicle(jdbc, "EXISTING-1", headquarters);

            Flyway.configure().dataSource(source).load().migrate();

            assertThat(jdbc.queryForObject("SELECT id FROM location WHERE is_headquarters", Long.class))
                    .isEqualTo(headquarters);
            assertThat(jdbc.queryForObject("SELECT home_hub_location_id FROM vehicle WHERE license_plate = 'EXISTING-1'",
                    Long.class)).isEqualTo(headquarters);
            assertThatThrownBy(() -> jdbc.update("UPDATE location SET is_headquarters = true WHERE id = ?", satellite))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uk_location_headquarters");
            assertThatThrownBy(() -> jdbc.update("UPDATE location SET is_headquarters = true WHERE id = ?", rest))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_location_headquarters_type");
        }
    }

    @Test
    void refusesToGuessAmongMultipleExistingHomeHubs() {
        try (var postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            var source = source(postgres);
            migrateBefore(source);
            var jdbc = new JdbcTemplate(source);
            vehicle(jdbc, "EXISTING-1", location(jdbc, "HUB-1", "HUB"));
            vehicle(jdbc, "EXISTING-2", location(jdbc, "HUB-2", "HUB"));

            assertThatThrownBy(() -> Flyway.configure().dataSource(source).load().migrate())
                    .isInstanceOf(FlywayException.class).hasMessageContaining("multiple home hubs");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM vehicle", Long.class)).isEqualTo(2);
        }
    }

    private static DriverManagerDataSource source(PostgreSQLContainer postgres) {
        return new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void migrateBefore(DriverManagerDataSource source) {
        Flyway.configure().dataSource(source).target("202611231300").load().migrate();
    }

    private static long location(JdbcTemplate jdbc, String code, String type) {
        return jdbc.queryForObject("""
                INSERT INTO location (code, name, location_type, country_code, address, status)
                VALUES (?, ?, ?, 'VN', 'Test', 'ACTIVE') RETURNING id
                """, Long.class, code, code, type);
    }

    private static void vehicle(JdbcTemplate jdbc, String plate, long hub) {
        jdbc.update("""
                INSERT INTO vehicle (license_plate, vehicle_class_id, standard_stall_count, wide_stall_count,
                                     home_hub_location_id, status)
                VALUES (?, (SELECT id FROM vehicle_class WHERE code = 'LIGHT'), 1, 0, ?, 'ACTIVE')
                """, plate, hub);
    }
}
