package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.entity.AuditLog;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.AuditLogRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

// Ghi nhật ký kiểm toán trên DB thật, kiểm cột jsonb.
@PostgresIntegrationTest
@Transactional
class AuditServiceTest {

    @Autowired private AuditService auditService;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private AppUserRepository users;
    @Autowired private EntityManager em;

    @Test
    void recordsActorTargetAndSnapshotsAsJson() {
        Long manager = users.findByUsername("manager.dev").orElseThrow().getId();
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("unitPrice", new BigDecimal("6000000"));
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("unitPrice", new BigDecimal("6500000"));

        auditService.record(manager, "UPDATE_PRICE_ITEM", "price_item", 42L, before, after);
        em.flush();
        em.clear();

        AuditLog log = latest("UPDATE_PRICE_ITEM");
        assertThat(log.getActorUser().getId()).isEqualTo(manager);
        assertThat(log.getTargetTable()).isEqualTo("price_item");
        assertThat(log.getTargetId()).isEqualTo(42L);
        assertThat(log.getOldData()).contains("6000000");
        assertThat(log.getNewData()).contains("6500000");
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    void systemActionHasNoActorAndMayOmitSnapshots() {
        auditService.record(null, "EXPIRE_QUOTATION", "quotation", 7L, null, Map.of("status", "EXPIRED"));
        em.flush();
        em.clear();

        AuditLog log = latest("EXPIRE_QUOTATION");
        assertThat(log.getActorUser()).isNull();
        assertThat(log.getOldData()).isNull();
        assertThat(log.getNewData()).contains("EXPIRED");
    }

    @Test
    void invalidInputIsRejected() {
        assertThatThrownBy(() -> auditService.record(null, " ", "quotation", 7L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditService.record(null, "A".repeat(51), "quotation", 7L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditService.record(null, "EXPIRE_QUOTATION", "quotation", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditService.record(-1L, "EXPIRE_QUOTATION", "quotation", 7L, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private AuditLog latest(String action) {
        return auditLogs.findAll().stream().filter(l -> l.getAction().equals(action))
                .reduce((first, second) -> second).orElseThrow();
    }
}
