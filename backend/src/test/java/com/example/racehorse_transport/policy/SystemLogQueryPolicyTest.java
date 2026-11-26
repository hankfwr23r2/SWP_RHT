package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SystemLogQueryPolicyTest {

    private static final Instant T = Instant.parse("2031-03-05T00:00:00Z");

    @Test
    void rangeMustMoveForwardAndPageSizeIsFixed() {
        assertThatCode(() -> SystemLogQueryPolicy.requireValid(T, T.plusSeconds(1), 0, 50)).doesNotThrowAnyException();
        assertThatThrownBy(() -> SystemLogQueryPolicy.requireValid(T, T, 0, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemLogQueryPolicy.requireValid(T, T.plusSeconds(1), -1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SystemLogQueryPolicy.requireValid(T, T.plusSeconds(1), 0, 25)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordKeyIsANumericId() {
        assertThat(SystemLogQueryPolicy.targetId(" 42 ")).isEqualTo(42L);
        assertThat(SystemLogQueryPolicy.targetId("")).isNull();
        assertThatThrownBy(() -> SystemLogQueryPolicy.targetId("BK-1")).isInstanceOf(IllegalArgumentException.class);
    }
}
