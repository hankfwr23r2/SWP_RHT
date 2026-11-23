package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class PricingMigrationsTest {
    @Test
    void upgradesExistingPricesAndServicesAndPreservesConfiguredRoutingValues() {
        try (var postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).target("202611231600").load().migrate();
            var jdbc = new JdbcTemplate(source);
            Long list = jdbc.queryForObject("""
                    INSERT INTO price_list (name, currency_code, margin_rate, fuel_buffer_rate, deposit_rate,
                                            valid_from, status)
                    VALUES ('Existing prices', 'VND', 0, 0, 0, DATE '2026-01-01', 'DRAFT')
                    RETURNING id
                    """, Long.class);
            String insertPrice = """
                    INSERT INTO price_item (price_list_id, billable_service_id, km_from, km_to, unit_price)
                    SELECT ?, id, ?, ?, 100 FROM billable_service WHERE code = ?
                    """;
            jdbc.update(insertPrice, list, 0, 50, "DIRECT_KM");
            jdbc.update(insertPrice, list, null, null, "FEEDING");
            jdbc.update("""
                    INSERT INTO system_config (config_key, config_value, config_group, unit, min_value, max_value)
                    VALUES ('TRUCK_WEIGHT_TONS', '12', 'ROUTING', 'COUNT', 1, 60)
                    """);

            Flyway.configure().dataSource(source).target("202611231720").load().migrate();

            assertThat(jdbc.update(insertPrice, list, 50, null, "DIRECT_KM")).isEqualTo(1);
            assertThatThrownBy(() -> jdbc.update(insertPrice, list, null, 50, "DIRECT_KM"))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_price_item_km_pair");
            assertThatThrownBy(() -> jdbc.update(insertPrice, list, 80, 70, "DIRECT_KM"))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_price_item_km_range");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM price_item WHERE price_list_id = ? AND km_from = 0 AND km_to = 50
                    """, Long.class, list)).isEqualTo(1L);
            assertThat(jdbc.queryForList("""
                    SELECT config_key || ':' || config_value || ':' || unit || ':' || min_value || ':' || max_value
                    FROM system_config WHERE config_group = 'ROUTING'
                    """, String.class)).containsExactlyInAnyOrder(
                    "TRUCK_TOP_SPEED_KMH:60:COUNT:10:120", "TRUCK_WEIGHT_TONS:12:COUNT:1:60");
            assertThat(jdbc.queryForObject("""
                    SELECT b.status FROM billable_service b JOIN price_item p ON p.billable_service_id = b.id
                    WHERE b.code = 'FEEDING' AND p.price_list_id = ?
                    """, String.class, list)).isEqualTo("INACTIVE");
        }
    }
}
