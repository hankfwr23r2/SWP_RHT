package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class MultipleDeliveryAddressesMigrationTest {
    @Test
    void backfillsOldHorsesAndEnforcesPickupUniquenessAndDeliveryOwnership() {
        try (var postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).locations("classpath:db/migration", "classpath:db/seed-dev")
                    .target("202611251400").load().migrate();
            var jdbc = new JdbcTemplate(source);
            long firstBooking = booking(jdbc, "MULTIDROP-OLD");
            long secondBooking = booking(jdbc, "MULTIDROP-OTHER");
            long pickup = address(jdbc, firstBooking, "PICKUP", "Old pickup");
            long delivery = address(jdbc, firstBooking, "DELIVERY", "Old delivery");
            long foreignDelivery = address(jdbc, secondBooking, "DELIVERY", "Other booking");
            long horse = jdbc.queryForObject("""
                    INSERT INTO horse (owner_customer_id, name, sex, horse_type_id, status)
                    SELECT min(c.user_id), 'Migration test horse', 'MARE', (SELECT min(id) FROM horse_type), 'ACTIVE' FROM customer c
                    RETURNING id
                    """, Long.class);
            jdbc.update("""
                    INSERT INTO booking_horse (booking_id, horse_id, stall_type_code, status)
                    VALUES (?, ?, 'STANDARD', 'ACTIVE')
                    """, firstBooking, horse);

            Flyway.configure().dataSource(source).locations("classpath:db/migration", "classpath:db/seed-dev")
                    .load().migrate();

            assertThat(jdbc.queryForObject("SELECT delivery_address_id FROM booking_horse WHERE booking_id = ? AND horse_id = ?",
                    Long.class, firstBooking, horse)).isEqualTo(delivery);
            long extraDelivery = address(jdbc, firstBooking, "DELIVERY", "Second delivery");
            assertThat(extraDelivery).isNotEqualTo(delivery).isNotEqualTo(pickup);
            assertThatThrownBy(() -> address(jdbc, firstBooking, "PICKUP", "A second pickup"))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uk_booking_address_pickup");
            assertThatThrownBy(() -> jdbc.update("UPDATE booking_horse SET delivery_address_id = ? WHERE horse_id = ?",
                    foreignDelivery, horse)).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("fk_booking_horse_delivery_address");
            assertThat(jdbc.update("UPDATE booking_horse SET delivery_address_id = ? WHERE horse_id = ?", extraDelivery, horse))
                    .isEqualTo(1);
        }
    }

    private static long booking(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("""
                INSERT INTO booking (code, customer_id, booking_type, requested_pickup_date, status, created_by)
                SELECT ?, min(c.user_id), 'LOCAL', DATE '2030-01-01', 'PENDING_INTAKE', min(c.user_id) FROM customer c
                RETURNING id
                """, Long.class, code);
    }

    private static long address(JdbcTemplate jdbc, long bookingId, String type, String line) {
        return jdbc.queryForObject("""
                INSERT INTO booking_address (booking_id, address_type, address_line, province_code, country_code,
                                             latitude, longitude, contact_name, contact_phone)
                VALUES (?, ?, ?, 'VN-79', 'VN', 10.8, 106.8, 'Test', '0912345678')
                RETURNING id
                """, Long.class, bookingId, type, line);
    }
}
