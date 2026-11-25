package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.enums.IncidentPlanStatus;
import com.example.racehorse_transport.entity.enums.IncidentResolutionType;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.IncidentType;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentPolicyTest {

    private static final Instant REPORTED = Instant.parse("2031-03-05T10:00:00Z");

    @Test
    void tripIncidentReportRejectsComplaintsAndAllowsOperationalIncidents() {
        assertThatThrownBy(() -> IncidentPolicy.requireReportable(VehicleTripStatus.IN_PROGRESS, IncidentType.COMPLAINT, true))
                .isInstanceOf(DispatchRuleException.class)
                .hasMessageContaining("chi tiết đơn hàng");
        assertThatCode(() -> IncidentPolicy.requireReportable(VehicleTripStatus.IN_PROGRESS, IncidentType.OTHER, false))
                .doesNotThrowAnyException();
    }

    @Test
    void healthRequiresAHorseForNewReportsButLegacyHealthStillRequiresFit() {
        assertThatThrownBy(() -> IncidentPolicy.requireReportable(VehicleTripStatus.IN_PROGRESS, IncidentType.HEALTH, false))
                .hasMessageContaining("chọn con ngựa");
        assertThatThrownBy(() -> IncidentPolicy.requireReportable(VehicleTripStatus.COMPLETED, IncidentType.HEALTH, true))
                .hasMessageContaining("đang chạy");
        IncidentPolicy.requireReportable(VehicleTripStatus.IN_PROGRESS, IncidentType.ACCIDENT, false);
        assertThat(IncidentPolicy.requiresFit(IncidentType.HEALTH, false)).isTrue();
        assertThat(IncidentPolicy.requiresFit(IncidentType.ACCIDENT, true)).isTrue();
        assertThat(IncidentPolicy.requiresFit(IncidentType.ACCIDENT, false)).isFalse();
        assertThat(IncidentPolicy.requiresFit(IncidentType.HANDOVER_TIMEOUT, true)).isFalse();
        assertThatThrownBy(() -> IncidentPolicy.requireResolutionAvailable(IncidentType.HANDOVER_TIMEOUT, IncidentResolutionType.RETURN_TO_HUB))
                .hasMessageContaining("Xe giao thẳng");
    }

    @Test
    void tripWithAnOpenIncidentCannotReportAnother() {
        assertThat(IncidentPolicy.isOpen(IncidentStatus.PLAN_ACTIVE)).isTrue();
        assertThat(IncidentPolicy.isOpen(IncidentStatus.RESOLVED)).isFalse();
        assertThatCode(() -> IncidentPolicy.requireNoOpenTripIncident(false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> IncidentPolicy.requireNoOpenTripIncident(true)).hasMessageContaining("chưa đóng");
    }

    @Test
    void handlerMustHoldTheRoleTheResolutionNeeds() {
        assertThatCode(() -> IncidentPolicy.requireHandler(IncidentResolutionType.VET_TREATMENT, true, List.of(PartyRoleType.VET))).doesNotThrowAnyException();
        assertThatCode(() -> IncidentPolicy.requireHandler(IncidentResolutionType.CONTINUE, false, List.of())).doesNotThrowAnyException();
        assertThatCode(() -> IncidentPolicy.requireHandler(IncidentResolutionType.TRANSFER_VEHICLE, false, List.of())).doesNotThrowAnyException();
        assertThatThrownBy(() -> IncidentPolicy.requireHandler(IncidentResolutionType.VET_TREATMENT, false, List.of())).hasMessageContaining("cần chọn bên xử lý");
        assertThatThrownBy(() -> IncidentPolicy.requireHandler(IncidentResolutionType.STABLE_NEARBY, true, List.of(PartyRoleType.VET))).hasMessageContaining("không có vai trò phù hợp");
        assertThatThrownBy(() -> IncidentPolicy.requireHandler(IncidentResolutionType.CONTINUE, true, List.of(PartyRoleType.VET))).hasMessageContaining("không có vai trò phù hợp");
    }

    @Test
    void onlyTheLatestProposedPlanIsDecidedAndRejectionNeedsAReason() {
        assertThatCode(() -> IncidentPolicy.requireDecidable(IncidentPlanStatus.PROPOSED, true)).doesNotThrowAnyException();
        assertThatThrownBy(() -> IncidentPolicy.requireDecidable(IncidentPlanStatus.PROPOSED, false)).hasMessageContaining("mới nhất");
        assertThatThrownBy(() -> IncidentPolicy.requireDecidable(IncidentPlanStatus.APPROVED, true)).hasMessageContaining("mới nhất");
        assertThatCode(() -> IncidentPolicy.requireRejectionReason("Chi phí quá cao")).doesNotThrowAnyException();
        assertThatThrownBy(() -> IncidentPolicy.requireRejectionReason(" ")).hasMessageContaining("ghi lý do");
    }

    @Test
    void fitIsConfirmedAfterTheApprovedPlanAndEnoughRest() {
        assertThatCode(() -> IncidentPolicy.requireCanConfirmFit(IncidentStatus.PLAN_ACTIVE, REPORTED, REPORTED.plusSeconds(1800), 30)).doesNotThrowAnyException();
        assertThatThrownBy(() -> IncidentPolicy.requireCanConfirmFit(IncidentStatus.PLAN_PENDING, REPORTED, REPORTED.plusSeconds(3600), 30)).hasMessageContaining("chưa được duyệt");
        assertThatThrownBy(() -> IncidentPolicy.requireCanConfirmFit(IncidentStatus.PLAN_ACTIVE, REPORTED, REPORTED.plusSeconds(1799), 30)).hasMessageContaining("đủ 30 phút");
    }

    @Test
    void incidentIsResolvedOnlyWithAnActivePlanAndFitConfirmedForHorses() {
        assertThatCode(() -> IncidentPolicy.requireResolvable(IncidentStatus.PLAN_ACTIVE, true, true)).doesNotThrowAnyException();
        assertThatCode(() -> IncidentPolicy.requireResolvable(IncidentStatus.PLAN_ACTIVE, false, false)).doesNotThrowAnyException();
        assertThatThrownBy(() -> IncidentPolicy.requireResolvable(IncidentStatus.REPORTED, false, false)).hasMessageContaining("chưa có phương án");
        assertThatThrownBy(() -> IncidentPolicy.requireResolvable(IncidentStatus.PLAN_ACTIVE, true, false)).hasMessageContaining("đủ sức khỏe");
    }
}
