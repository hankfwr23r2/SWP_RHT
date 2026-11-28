package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import com.example.racehorse_transport.entity.enums.IncidentType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeliveryFaultPolicyTest {
    private final Instant failed = Instant.parse("2026-11-25T04:00:00Z");

    @Test
    void acceptsExactly24HoursAndRejectsTheNextSecond() {
        assertThat(DeliveryFaultPolicy.canAmend(IncidentType.HANDOVER_TIMEOUT, failed, failed.plusSeconds(86400))).isTrue();
        assertThat(DeliveryFaultPolicy.canAmend(IncidentType.HANDOVER_TIMEOUT, failed, failed.plusSeconds(86401))).isFalse();
        assertThat(DeliveryFaultPolicy.canAmend(IncidentType.DELAY, failed, failed)).isFalse();
    }

    @Test
    void coordinatorCanOnlyRecordForceMajeure() {
        assertThat(DeliveryFaultPolicy.canSelect("MANAGER", IncidentCauseType.RECIPIENT_ABSENT)).isTrue();
        assertThat(DeliveryFaultPolicy.canSelect("COORDINATOR", IncidentCauseType.FORCE_MAJEURE)).isTrue();
        assertThat(DeliveryFaultPolicy.canSelect("COORDINATOR", IncidentCauseType.COMPANY_FAULT)).isFalse();
        assertThat(DeliveryFaultPolicy.canSelect("ADMIN", IncidentCauseType.FORCE_MAJEURE)).isFalse();
    }
}
