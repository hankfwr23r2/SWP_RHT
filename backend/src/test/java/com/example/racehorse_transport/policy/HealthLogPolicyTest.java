package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.enums.HealthStatus;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import java.time.Instant;
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
    void supportsHubsAndPartnerStablesIncludingOvernightRestLocations() {
        assertThat(HealthLogPolicy.canRecordStay(LocationType.HUB, HorseStayType.ASSEMBLY, false)).isTrue();
        assertThat(HealthLogPolicy.canRecordStay(LocationType.STABLE, HorseStayType.STABLING, true)).isTrue();
        assertThat(HealthLogPolicy.canRecordStay(LocationType.REST_STOP, HorseStayType.OVERNIGHT, true)).isTrue();
        assertThat(HealthLogPolicy.canRecordStay(LocationType.REST_STOP, HorseStayType.OVERNIGHT, false)).isFalse();
        assertThat(HealthLogPolicy.canRecordStay(LocationType.QUARANTINE_FARM, HorseStayType.QUARANTINE, true)).isFalse();
    }

    @Test
    void hubReportNeedsOnlyTheReporter() {
        assertThat(HealthLogPolicy.reportedNote(" Lan ")).isEqualTo("Người báo: Lan");
        assertThatThrownBy(() -> HealthLogPolicy.reportedNote(" "))
                .isInstanceOfSatisfying(DispatchRuleException.class, error -> assertThat(error.getField()).isEqualTo("reportedBy"));
    }

    @Test
    void vetNoteRaisesToAtLeastWatch() {
        assertThat(HealthLogPolicy.atLeastWatch(HealthStatus.NORMAL)).isEqualTo(HealthStatus.WATCH);
        assertThat(HealthLogPolicy.atLeastWatch(HealthStatus.SICK)).isEqualTo(HealthStatus.SICK);
    }

    @Test
    void restStopIsLeftOnlyWhenRested() {
        assertThatCode(() -> HealthLogPolicy.requireRested(ARRIVED, ARRIVED.plusSeconds(30 * 60), 30, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> HealthLogPolicy.requireRested(ARRIVED, ARRIVED.plusSeconds(29 * 60), 30, " "))
                .hasMessageContaining("Chưa nghỉ đủ 30 phút");
        assertThatCode(() -> HealthLogPolicy.requireRested(ARRIVED, ARRIVED.plusSeconds(10 * 60), 30, "Ngựa ổn, kịp giờ cửa khẩu")).doesNotThrowAnyException();
    }
}
