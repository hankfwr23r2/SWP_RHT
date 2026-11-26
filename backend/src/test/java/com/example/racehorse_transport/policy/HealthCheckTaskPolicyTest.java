package com.example.racehorse_transport.policy;

import static com.example.racehorse_transport.entity.enums.HealthCheckTaskStatus.*;
import static org.assertj.core.api.Assertions.*;

import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.entity.enums.HealthCheckType;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HealthCheckTaskPolicyTest {
    private static final Instant NOW = Instant.parse("2026-11-29T12:00:00Z");

    @Test
    void onlyLinehaulAndDeliveryNeedHubExitChecks() {
        assertThat(HealthCheckTaskPolicy.hubExitType(VehicleTripType.LINEHAUL)).contains(HealthCheckType.BEFORE_DEPARTURE);
        assertThat(HealthCheckTaskPolicy.hubExitType(VehicleTripType.DELIVERY)).contains(HealthCheckType.BEFORE_DELIVERY);
        assertThat(HealthCheckTaskPolicy.hubExitType(VehicleTripType.TRANSFER)).isEmpty();
        assertThat(HealthCheckTaskPolicy.hubExitType(VehicleTripType.DIRECT)).isEmpty();
        assertThat(HealthCheckTaskPolicy.hubExitType(VehicleTripType.PICKUP)).isEmpty();
    }

    @Test
    void derivesOverdueOnlyForUnfinishedTasksPastTheirDeadline() {
        assertThat(HealthCheckTaskPolicy.overdue(ASSIGNED, NOW.minusSeconds(1), NOW)).isTrue();
        assertThat(HealthCheckTaskPolicy.overdue(ACCEPTED, NOW, NOW)).isFalse();
        assertThat(HealthCheckTaskPolicy.overdue(DONE, NOW.minusSeconds(1), NOW)).isFalse();
        assertThat(HealthCheckTaskPolicy.overdue(CANCELLED, NOW.minusSeconds(1), NOW)).isFalse();
    }

    @Test
    void validatesTransitionsAndFutureDeadline() {
        assertThatCode(() -> HealthCheckTaskPolicy.requireTransition(ASSIGNED, ACCEPTED)).doesNotThrowAnyException();
        assertThatCode(() -> HealthCheckTaskPolicy.requireTransition(ASSIGNED, DONE)).doesNotThrowAnyException();
        assertThatCode(() -> HealthCheckTaskPolicy.requireTransition(ACCEPTED, CANCELLED)).doesNotThrowAnyException();
        assertThatThrownBy(() -> HealthCheckTaskPolicy.requireTransition(DONE, CANCELLED)).isInstanceOf(DispatchRuleException.class);
        assertThatThrownBy(() -> HealthCheckTaskPolicy.requireTransition(ACCEPTED, ACCEPTED)).isInstanceOf(DispatchRuleException.class);
        assertThatThrownBy(() -> HealthCheckTaskPolicy.requireFutureDue(NOW, NOW)).isInstanceOf(DispatchRuleException.class);
    }
}
