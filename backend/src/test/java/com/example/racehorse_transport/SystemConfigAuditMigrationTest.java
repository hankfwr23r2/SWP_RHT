package com.example.racehorse_transport;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemConfigAuditMigrationTest {

    @Test
    void assignsStableUniqueAuditIdsToExistingAndNewConfigs() {
        try (var postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).target("202611231400").load().migrate();
            var jdbc = new JdbcTemplate(source);
            jdbc.update("update system_config set config_value = '8' where config_key = 'OTP_MAX_ATTEMPTS'");
            long before = jdbc.queryForObject("select count(*) from system_config", Long.class);

            Flyway.configure().dataSource(source).load().migrate();

            assertThat(jdbc.queryForObject("select count(distinct audit_target_id) from system_config", Long.class))
                    .isEqualTo(before);
            Long id = jdbc.queryForObject("""
                    select audit_target_id from system_config where config_key = 'OTP_MAX_ATTEMPTS' and config_value = '8'
                    """, Long.class);
            jdbc.update("update system_config set config_value = '5' where config_key = 'OTP_MAX_ATTEMPTS'");
            assertThat(jdbc.queryForObject("""
                    select audit_target_id from system_config where config_key = 'OTP_MAX_ATTEMPTS'
                    """, Long.class)).isEqualTo(id);
            Long newId = jdbc.queryForObject("""
                    insert into system_config (config_key, config_value, unit, config_group)
                    values ('MIGRATION_TEST', '2', 'COUNT', 'TEST') returning audit_target_id
                    """, Long.class);
            assertThat(newId).isPositive().isNotEqualTo(id);
            assertThatThrownBy(() -> jdbc.update("""
                    insert into system_config (config_key, config_value, config_group, audit_target_id)
                    overriding system value values ('COLLISION', '2', 'TEST', ?)
                    """, id)).isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("uk_system_config_audit_target_id");
        }
    }
}
