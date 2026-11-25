package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.enums.PickupMissReason;
import com.example.racehorse_transport.exception.DispatchRuleException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PickupMissPolicyTest {
    private static final Instant NOW = Instant.parse("2026-12-07T00:00:00Z");
    private static final Duration LONG = Duration.ofHours(12);
    private static final Duration SHORT = Duration.ofHours(1);

    @Test
    void capsChoiceAtCheckIn() {
        var checkInAt = NOW.plus(Duration.ofMinutes(25));
        assertThat(PickupMissPolicy.choiceDueAt(NOW, NOW.plus(Duration.ofHours(25)), LONG, SHORT))
                .isEqualTo(NOW.plus(LONG));
        assertThat(PickupMissPolicy.choiceDueAt(NOW, checkInAt, LONG, SHORT)).isEqualTo(checkInAt);
        assertThat(PickupMissPolicy.choiceDueAt(NOW, NOW.minusSeconds(1), LONG, SHORT)).isEqualTo(NOW);
    }

    @Test
    void retryNeedsPreparationAndAnOnTimeArrival() {
        var checkInAt = NOW.plus(Duration.ofHours(3));
        assertThat(PickupMissPolicy.retryFits(NOW, NOW, checkInAt, checkInAt)).isTrue();
        assertThat(PickupMissPolicy.retryFits(NOW, NOW.minusSeconds(1), checkInAt, checkInAt)).isFalse();
        assertThat(PickupMissPolicy.retryFits(NOW, NOW, checkInAt.plusSeconds(1), checkInAt)).isFalse();
    }

    @Test
    void absenceRequiresWaitingAndEveryCall() {
        var wait = Duration.ofMinutes(30);
        assertThatThrownBy(() -> PickupMissPolicy.requireEvidence(PickupMissReason.SENDER_ABSENT,
                NOW, NOW.plusSeconds(1799), wait, "No answer", "No answer", "No answer"))
                .isInstanceOf(DispatchRuleException.class);
        assertThatThrownBy(() -> PickupMissPolicy.requireEvidence(PickupMissReason.SENDER_ABSENT,
                NOW, NOW.plus(wait), wait, "No answer", "No answer", null))
                .isInstanceOf(DispatchRuleException.class);
        PickupMissPolicy.requireEvidence(PickupMissReason.SENDER_ABSENT, NOW, NOW.plus(wait), wait,
                "No answer", "No answer", "No answer");
    }
}
