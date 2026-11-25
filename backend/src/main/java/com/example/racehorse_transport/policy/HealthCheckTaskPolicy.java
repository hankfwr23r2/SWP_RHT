package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.HealthCheckTaskStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import java.time.Instant;
import java.util.Set;

public final class HealthCheckTaskPolicy {
    public static final Set<HealthCheckTaskStatus> OPEN = Set.of(HealthCheckTaskStatus.ASSIGNED, HealthCheckTaskStatus.ACCEPTED);

    private HealthCheckTaskPolicy() {}

    public static boolean overdue(HealthCheckTaskStatus status, Instant dueAt, Instant now) {
        return OPEN.contains(status) && dueAt.isBefore(now);
    }

    public static void requireTransition(HealthCheckTaskStatus status, HealthCheckTaskStatus target) {
        boolean allowed = switch (target) {
            case ACCEPTED -> status == HealthCheckTaskStatus.ASSIGNED;
            case DONE, CANCELLED -> OPEN.contains(status);
            case ASSIGNED -> false;
        };
        if (!allowed) throw new DispatchRuleException("status", "Việc khám đã đổi trạng thái, vui lòng tải lại.", true);
    }

    public static void requireFutureDue(Instant dueAt, Instant now) {
        if (!dueAt.isAfter(now)) throw new DispatchRuleException("dueAt", "Hạn khám phải ở tương lai.", false);
    }
}
