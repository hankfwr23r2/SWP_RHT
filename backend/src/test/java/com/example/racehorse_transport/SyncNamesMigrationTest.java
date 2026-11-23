package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class SyncNamesMigrationTest {

    @Test
    void upgradesExistingRowsAndKeepsDatabaseObjectsWorking() {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(dataSource).target("202611231200").load().migrate();
            var jdbc = new JdbcTemplate(dataSource);
            jdbc.update("""
                    INSERT INTO location (code, name, type, country_code, address, opening_minutes, closing_minutes, status)
                    VALUES ('SYNC-GATE', 'Gate', 'BORDER_GATE', 'VN', 'Gate', 0, 1439, 'ACTIVE'),
                           ('SYNC-HUB', 'Hub', 'HUB', 'VN', 'Hub', NULL, NULL, 'ACTIVE')
                    """);
            Long serviceId = jdbc.queryForObject("""
                    INSERT INTO service (code, name, layer, billing_unit, auto_apply, status)
                    VALUES ('SYNC-OLD', 'Old service', 'ADD_ON', 'PER_BOOKING', true, 'ACTIVE') RETURNING id
                    """, Long.class);
            Long priceListId = jdbc.queryForObject("""
                    INSERT INTO price_list (name, currency, margin_rate, fuel_buffer_rate, deposit_rate, valid_from, status)
                    VALUES ('Sync price list', 'VND', 0, 0, 0, DATE '2026-01-01', 'DRAFT') RETURNING id
                    """, Long.class);
            jdbc.update("INSERT INTO price_item (price_list_id, service_id, unit_price) VALUES (?, ?, 1)",
                    priceListId, serviceId);

            Flyway.configure().dataSource(dataSource).load().migrate();

            assertThat(jdbc.queryForList("""
                    SELECT location_type, opening_time::text AS opens, closing_time::text AS closes
                    FROM location WHERE code LIKE 'SYNC-%' ORDER BY code
                    """)).satisfiesExactly(
                    gate -> assertThat(gate.values()).containsExactly("BORDER_GATE", "00:00:00", "23:59:00"),
                    hub -> assertThat(hub.values()).containsExactlyElementsOf(java.util.Arrays.asList("HUB", null, null)));
            assertThat(jdbc.queryForObject(
                    "SELECT is_auto_applied FROM billable_service WHERE id = ?", Boolean.class, serviceId)).isTrue();
            assertThat(jdbc.queryForObject(
                    "SELECT billable_service_id FROM price_item WHERE price_list_id = ?", Long.class, priceListId))
                    .isEqualTo(serviceId);
            assertThat(jdbc.queryForObject("""
                    INSERT INTO billable_service (code, name, layer, billing_unit, status)
                    VALUES ('SYNC-NEW', 'New service', 'ADD_ON', 'PER_BOOKING', 'ACTIVE') RETURNING id
                    """, Long.class)).isGreaterThan(serviceId);
            assertThat(jdbc.queryForObject("SELECT pg_get_serial_sequence('billable_service', 'id')", String.class))
                    .isEqualTo("public.billable_service_id_seq");
            assertThat(jdbc.queryForList("""
                    SELECT relname FROM pg_class
                    WHERE relname IN ('service', 'handover_item', 'ix_location_type', 'handover_document', 'ix_location_location_type')
                    """, String.class)).containsExactlyInAnyOrder("handover_document", "ix_location_location_type");
            assertThatThrownBy(() -> jdbc.update("DELETE FROM billable_service WHERE id = ?", serviceId))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("fk_price_item_billable_service_id");
        }
    }
}
