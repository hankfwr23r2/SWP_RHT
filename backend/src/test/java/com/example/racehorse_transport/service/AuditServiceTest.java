package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.AppUserRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// Ghi nhật ký kiểm toán trên DB thật, kiểm cột jsonb bằng SQL.
@PostgresIntegrationTest
@Transactional
class AuditServiceTest {

    @Autowired private AuditService audit;
    @Autowired private AppUserRepository accounts;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void recordsOperatorAndObjectSnapshots() {
        Long actor = accounts.findByUsername("admin.dev").orElseThrow().getId();
        audit.record(actor, "TEST_AUDIT", "system_config", 123L, Map.of("configValue", "1"), Map.of("configValue", "2"));

        var row = jdbc.queryForMap("""
                select actor_user_id, old_data ->> 'configValue' as previous_value, new_data ->> 'configValue' as new_value
                from audit_log where action = 'TEST_AUDIT' and target_id = 123
                """);
        assertThat(row).containsEntry("actor_user_id", actor).containsEntry("previous_value", "1").containsEntry("new_value", "2");
    }

    @Test
    void supportsSystemActionsAndAbsentSnapshots() {
        audit.record(null, "TEST_SYSTEM", "system_config", 124L, null, Map.of("configValue", "2"));
        assertThat(jdbc.queryForObject("""
                select count(*) from audit_log where action = 'TEST_SYSTEM' and target_id = 124
                    and actor_user_id is null and old_data is null and jsonb_typeof(new_data) = 'object'
                """, Long.class)).isEqualTo(1L);
    }

    @Test
    void invalidInputIsRejected() {
        assertThatThrownBy(() -> audit.record(null, " ", "quotation", 7L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> audit.record(null, "A".repeat(51), "quotation", 7L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> audit.record(null, "EXPIRE_QUOTATION", "quotation", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> audit.record(-1L, "EXPIRE_QUOTATION", "quotation", 7L, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
