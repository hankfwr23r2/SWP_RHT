package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import com.example.racehorse_transport.entity.enums.IncidentType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import java.time.Duration;
import java.time.Instant;

public final class DeliveryFaultPolicy {
    public static final Duration AMENDMENT_PERIOD = Duration.ofHours(24);

    private DeliveryFaultPolicy() {}

    public static boolean canAmend(IncidentType type, Instant failedAt, Instant now) {
        return type == IncidentType.HANDOVER_TIMEOUT && failedAt != null
                && !now.isAfter(failedAt.plus(AMENDMENT_PERIOD));
    }

    public static boolean canSelect(String role, IncidentCauseType cause) {
        return "MANAGER".equals(role) || ("COORDINATOR".equals(role) && cause == IncidentCauseType.FORCE_MAJEURE);
    }

    public static void requireAmendable(IncidentType type, Instant failedAt, Instant now) {
        if (!canAmend(type, failedAt, now)) {
            throw new DispatchRuleException("cause", "Chỉ sửa kết luận giao hụt trong vòng 24 giờ.", true);
        }
    }
}
