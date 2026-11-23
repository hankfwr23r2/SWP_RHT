package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.enums.HealthStatus;
import com.example.racehorse_transport.entity.enums.IntakeLevel;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class HealthLogPolicyTest {

    private static final Instant ARRIVED = Instant.parse("2031-03-05T10:00:00Z");

    @Test
    void logBelongsToExactlyOnePlace() {
        assertThatCode(() -> HealthLogPolicy.requireOnePlace(true, false)).doesNotThrowAnyException();
        assertThatCode(() -> HealthLogPolicy.requireOnePlace(false, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> HealthLogPolicy.requireOnePlace(true, true)).hasMessageContaining("một điểm dừng hoặc");
        assertThatThrownBy(() -> HealthLogPolicy.requireOnePlace(false, false)).hasMessageContaining("một điểm dừng hoặc");
    }

    @Test
    void refusingWaterTwiceRaisesToWatch() {
        assertThat(HealthLogPolicy.refusedWaterTwice(IntakeLevel.REFUSED, IntakeLevel.REFUSED)).isTrue();
        assertThat(HealthLogPolicy.refusedWaterTwice(IntakeLevel.REFUSED, IntakeLevel.REDUCED)).isFalse();
        assertThat(HealthLogPolicy.refusedWaterTwice(IntakeLevel.NORMAL, IntakeLevel.REFUSED)).isFalse();
        assertThat(HealthLogPolicy.refusedWaterTwice(IntakeLevel.REFUSED, null)).isFalse();
        assertThat(HealthLogPolicy.atLeastWatch(HealthStatus.NORMAL)).isEqualTo(HealthStatus.WATCH);
        assertThat(HealthLogPolicy.atLeastWatch(HealthStatus.SICK)).isEqualTo(HealthStatus.SICK);
    }

    @Test
    void restStopIsLeftOnlyWhenEveryHorseIsLoggedAndRested() {
        assertThatCode(() -> HealthLogPolicy.requireAllLogged(List.of())).doesNotThrowAnyException();
        assertThatThrownBy(() -> HealthLogPolicy.requireAllLogged(List.of("Sao Mai", "Gió Bắc"))).hasMessageContaining("Sao Mai, Gió Bắc");

        assertThatCode(() -> HealthLogPolicy.requireRested(ARRIVED, ARRIVED.plusSeconds(30 * 60), 30, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> HealthLogPolicy.requireRested(ARRIVED, ARRIVED.plusSeconds(29 * 60), 30, " "))
                .hasMessageContaining("Chưa nghỉ đủ 30 phút");
        assertThatCode(() -> HealthLogPolicy.requireRested(ARRIVED, ARRIVED.plusSeconds(10 * 60), 30, "Ngựa ổn, kịp giờ cửa khẩu")).doesNotThrowAnyException();
    }
}
