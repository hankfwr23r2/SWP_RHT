package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class TripExecutionPolicyTest {

    @Test
    void tripStartsOnlyWhenPlannedAndDriverAcknowledged() {
        assertThatCode(() -> TripExecutionPolicy.requireStartable(VehicleTripStatus.PLANNED, true)).doesNotThrowAnyException();
        assertThatCode(() -> TripExecutionPolicy.requireStartable(VehicleTripStatus.DISPATCHED, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> TripExecutionPolicy.requireStartable(VehicleTripStatus.IN_PROGRESS, true)).hasMessageContaining("đã chạy");
        assertThatThrownBy(() -> TripExecutionPolicy.requireStartable(VehicleTripStatus.PLANNED, false)).hasMessageContaining("Tài xế chưa xác nhận");
        assertThatThrownBy(() -> TripExecutionPolicy.requireInProgress(VehicleTripStatus.PLANNED)).hasMessageContaining("chưa xuất phát");
    }

    @Test
    void stopsAreVisitedInOrder() {
        assertThatCode(() -> TripExecutionPolicy.requireCanArrive(false, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> TripExecutionPolicy.requireCanArrive(true, true)).hasMessageContaining("Đã ghi nhận tới");
        assertThatThrownBy(() -> TripExecutionPolicy.requireCanArrive(false, false)).hasMessageContaining("Chưa rời điểm dừng trước");

        assertThatCode(() -> TripExecutionPolicy.requireCanDepart(true, false, 0)).doesNotThrowAnyException();
        assertThatThrownBy(() -> TripExecutionPolicy.requireCanDepart(false, false, 0)).hasMessageContaining("Chưa tới");
        assertThatThrownBy(() -> TripExecutionPolicy.requireCanDepart(true, true, 0)).hasMessageContaining("Đã rời");
        assertThatThrownBy(() -> TripExecutionPolicy.requireCanDepart(true, false, 1)).hasMessageContaining("giao nhận chưa xong");
    }

    @Test
    void handoverTypeMustFitTheStop() {
        assertThatCode(() -> TripExecutionPolicy.requireHandoverFitsStop(HandoverType.PICKUP, TripStopType.PICKUP)).doesNotThrowAnyException();
        assertThatCode(() -> TripExecutionPolicy.requireHandoverFitsStop(HandoverType.DELIVERY, TripStopType.DELIVERY)).doesNotThrowAnyException();
        assertThatCode(() -> TripExecutionPolicy.requireHandoverFitsStop(HandoverType.HUB_IN, TripStopType.HUB)).doesNotThrowAnyException();
        assertThatCode(() -> TripExecutionPolicy.requireHandoverFitsStop(HandoverType.HUB_OUT, TripStopType.HUB)).doesNotThrowAnyException();
        assertThatThrownBy(() -> TripExecutionPolicy.requireHandoverFitsStop(HandoverType.PICKUP, TripStopType.HUB)).hasMessageContaining("PICKUP");
        assertThatThrownBy(() -> TripExecutionPolicy.requireHandoverFitsStop(HandoverType.HUB_IN, TripStopType.REST)).hasMessageContaining("REST");
        assertThatThrownBy(() -> TripExecutionPolicy.requireHandoverFitsStop(HandoverType.AGENT, TripStopType.BORDER)).hasMessageContaining("AGENT");
        assertThat(TripExecutionPolicy.isBoarding(HandoverType.PICKUP)).isTrue();
        assertThat(TripExecutionPolicy.isBoarding(HandoverType.HUB_OUT)).isTrue();
        assertThat(TripExecutionPolicy.isBoarding(HandoverType.DELIVERY)).isFalse();
        assertThat(TripExecutionPolicy.isBoarding(HandoverType.HUB_IN)).isFalse();
    }

    @Test
    void chipIsComparedAfterNormalizing() {
        assertThat(TripExecutionPolicy.chipMatches("985 000-000 100002", "985000000100002")).isTrue();
        assertThat(TripExecutionPolicy.chipMatches("985000000100003", "985000000100002")).isFalse();
        assertThat(TripExecutionPolicy.chipMatches("", "")).isFalse();
        assertThatCode(() -> TripExecutionPolicy.requireSomeMatched(List.of(false, true))).doesNotThrowAnyException();
        assertThatThrownBy(() -> TripExecutionPolicy.requireSomeMatched(List.of(false))).hasMessageContaining("quét đúng chip");
        assertThatThrownBy(() -> TripExecutionPolicy.requireSomeMatched(List.of())).hasMessageContaining("quét đúng chip");
    }

    @Test
    void tripCompletesAtLastStopWithNoHorseLeftOnBoard() {
        assertThatCode(() -> TripExecutionPolicy.requireCanComplete(true, 0)).doesNotThrowAnyException();
        assertThatThrownBy(() -> TripExecutionPolicy.requireCanComplete(false, 0)).hasMessageContaining("điểm dừng cuối");
        assertThatThrownBy(() -> TripExecutionPolicy.requireCanComplete(true, 2)).hasMessageContaining("Còn 2 con");
    }
}
