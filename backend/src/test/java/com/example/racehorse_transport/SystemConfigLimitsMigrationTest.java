package com.example.racehorse_transport;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemConfigLimitsMigrationTest {

    @Test
    void rejectsInvalidExistingValuesWithRollbackAndAddsLimitsAfterCorrection() {
        try (var postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).target("202611231500").load().migrate();
            var jdbc = new JdbcTemplate(source);
            var migration = Flyway.configure().dataSource(source).target("202611231600").load();
            jdbc.update("""
                    insert into system_config (config_key, config_value, config_group)
                    values ('MIGRATION_TEST', 'unbounded', 'TEST')
                    """);

            assertRejectedAndRolledBack(migration, jdbc, "11", "must be between 1 and 10");
            assertRejectedAndRolledBack(migration, jdbc, "1.5", "must contain an integer");
            assertRejectedAndRolledBack(migration, jdbc, "9".repeat(500), "must be between 1 and 10");

            jdbc.update("update system_config set config_value = ' +0010 ' where config_key = 'OTP_MAX_ATTEMPTS'");
            migration.migrate();

            assertThat(jdbc.queryForList("""
                    select config_key || ':' || min_value || ':' || max_value
                    from system_config where min_value is not null
                    """, String.class)).containsExactlyInAnyOrder(
                    "DEPARTURE_GENERATION_WEEKS:1:52", "OTP_EXPIRY_MINUTES:1:30", "OTP_MAX_ATTEMPTS:1:10",
                    "QUOTATION_VALID_HOURS:1:720", "BALANCE_DUE_DAYS_BEFORE_PICKUP:1:30",
                    "HANDOVER_FREE_WAIT_MINUTES:1:1440");
            assertThat(currentValue(jdbc)).isEqualTo(" +0010 ");
            assertThat(jdbc.queryForMap("""
                    select min_value, max_value from system_config where config_key = 'MIGRATION_TEST'
                    """)).containsEntry("min_value", null).containsEntry("max_value", null);
            assertThatThrownBy(() -> jdbc.update("""
                    update system_config set max_value = null where config_key = 'OTP_MAX_ATTEMPTS'
                    """)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_system_config_limits");
            assertThatThrownBy(() -> jdbc.update("""
                    update system_config set min_value = 11 where config_key = 'OTP_MAX_ATTEMPTS'
                    """)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_system_config_limits");
        }
    }

    private void assertRejectedAndRolledBack(Flyway migration, JdbcTemplate jdbc, String value, String reason) {
        jdbc.update("update system_config set config_value = ? where config_key = 'OTP_MAX_ATTEMPTS'", value);
        assertThatThrownBy(migration::migrate).isInstanceOf(FlywayException.class)
                .hasMessageContaining("system_config OTP_MAX_ATTEMPTS " + reason);
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = current_schema() and table_name = 'system_config'
                    and column_name in ('min_value', 'max_value')
                """, Long.class)).isZero();
        assertThat(currentValue(jdbc)).isEqualTo(value);
    }

    private String currentValue(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                select config_value from system_config where config_key = 'OTP_MAX_ATTEMPTS'
                """, String.class);
    }
}
