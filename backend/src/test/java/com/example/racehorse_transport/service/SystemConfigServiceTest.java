package com.example.racehorse_transport.service;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.dto.config.SystemConfigUpdateRequestDto;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.exception.SystemConfigRuleException;
import com.example.racehorse_transport.repository.SystemConfigRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@PostgresIntegrationTest
class SystemConfigServiceTest {

    private static final String KEY = "OTP_MAX_ATTEMPTS";

    @Autowired private SystemConfigService configs;
    @Autowired private SystemConfigRepository repository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void changesConfigAndAuditInSameTransactionAndRollsBackTogether() {
        var prior = configs.configuration(KEY);
        long targetId = repository.findById(KEY).orElseThrow().getAuditTargetId();
        long auditCount = auditCount(targetId);

        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            var request = new SystemConfigUpdateRequestDto(BigDecimal.valueOf(Integer.parseInt(prior.configValue()) + 1L));
            var updated = configs.updateConfiguration(KEY, "admin.dev", request);
            entityManager.flush();
            entityManager.clear();
            assertThat(configs.configuration(KEY)).isEqualTo(updated);
            assertThat(updated.updatedByUserId()).isNotNull();
            assertThat(configs.getInt(KEY)).isEqualTo(request.integerValue());
            assertThat(configs.configurations()).contains(updated);

            var snapshot = jdbc.queryForMap("""
                    select actor_user_id, old_data ->> 'configValue' as previous_value,
                           new_data ->> 'configValue' as new_value, new_data ->> 'configKey' as config_key
                    from audit_log where action = 'UPDATE_SYSTEM_CONFIG' and target_table = 'system_config'
                        and target_id = ? order by id desc limit 1
                    """, targetId);
            assertThat(snapshot).containsEntry("actor_user_id", updated.updatedByUserId())
                    .containsEntry("previous_value", prior.configValue()).containsEntry("new_value", updated.configValue())
                    .containsEntry("config_key", KEY);
            assertThat(configs.updateConfiguration(KEY, "admin.dev", request)).isEqualTo(updated);
            assertThat(auditCount(targetId)).isEqualTo(auditCount + 1);
            transaction.setRollbackOnly();
        });

        assertThat(configs.configuration(KEY)).isEqualTo(prior);
        assertThat(auditCount(targetId)).isEqualTo(auditCount);
    }

    @Test
    void rejectsInvalidValueMissingKeyAndNonAdmin() {
        var request = new SystemConfigUpdateRequestDto(BigDecimal.ONE);
        assertThatThrownBy(() -> configs.updateConfiguration(KEY, "admin.dev", new SystemConfigUpdateRequestDto(BigDecimal.ZERO)))
                .isInstanceOf(SystemConfigRuleException.class);
        assertThatThrownBy(() -> configs.updateConfiguration("INVALID_KEY", "admin.dev", request))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> configs.updateConfiguration(KEY, "customer1.dev", request))
                .isInstanceOf(AccessDeniedException.class);
    }

    private long auditCount(long targetId) {
        return jdbc.queryForObject("""
                select count(*) from audit_log where action = 'UPDATE_SYSTEM_CONFIG' and target_id = ?
                """, Long.class, targetId);
    }
}
