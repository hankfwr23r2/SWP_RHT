package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.IncidentPlan;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.IncidentType;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.IncidentPlanStatus;
import com.example.racehorse_transport.entity.enums.IncidentResolutionType;
import com.example.racehorse_transport.entity.enums.IncidentSeverity;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// Quy tắc sự cố và phương án (B4), hàm thuần
public final class IncidentPolicy {

    public static final Comparator<Incident> DISPLAY_ORDER = Comparator
            .comparing(Incident::getSeverity, Comparator.reverseOrder())
            .thenComparing(Incident::getReportedAt, Comparator.reverseOrder())
            .thenComparing(Incident::getId, Comparator.reverseOrder());

    private IncidentPolicy() {}

    public static IncidentSeverity defaultSeverity(IncidentType type) {
        return switch (type) {
            case HEALTH, ACCIDENT -> IncidentSeverity.HIGH;
            case VEHICLE_BREAKDOWN, HANDOVER_TIMEOUT, DOCUMENT -> IncidentSeverity.MEDIUM;
            case DELAY, OTHER, COMPLAINT -> IncidentSeverity.LOW;
        };
    }

    public static boolean blocksTrip(IncidentSeverity severity, IncidentStatus status, Instant tripUnlockedAt) {
        return severity == IncidentSeverity.HIGH && isOpen(status)
                && status != IncidentStatus.PLAN_ACTIVE && tripUnlockedAt == null;
    }

    public static boolean requiresFit(IncidentType type, boolean hasHorse) {
        return type == IncidentType.HEALTH || (type == IncidentType.ACCIDENT && hasHorse);
    }

    public static void requireFitConfirmation(IncidentType type, boolean hasHorse) {
        if (!requiresFit(type, hasHorse)) {
            throw new DispatchRuleException("incidentType", "Sự cố này không yêu cầu xác nhận đủ sức.", true);
        }
    }

    public static void requireReportable(VehicleTripStatus status, IncidentType type, boolean hasHorse) {
        if (status != VehicleTripStatus.IN_PROGRESS) {
            throw new DispatchRuleException("vehicleTripId", "Chỉ báo sự cố khi lượt xe đang chạy.", true);
        }
        if (type == IncidentType.HANDOVER_TIMEOUT) {
            throw new DispatchRuleException("incidentType", "Chỉ hệ thống mở sự cố quá giờ chờ giao nhận.");
        }
        if (type == IncidentType.COMPLAINT) {
            throw new DispatchRuleException("incidentType", "Khiếu nại được ghi nhận trong chi tiết đơn hàng.");
        }
        if (type == IncidentType.HEALTH && !hasHorse) {
            throw new DispatchRuleException("horseLegId", "Sự cố sức khỏe phải chọn con ngựa.");
        }
    }

    public static void requireResolutionAvailable(IncidentType type, IncidentResolutionType resolution) {
        if (type == IncidentType.HANDOVER_TIMEOUT && resolution == IncidentResolutionType.RETURN_TO_HUB) {
            throw new DispatchRuleException("resolutionType", "Xe giao thẳng vào hub, không cần bấm đưa về hub.", true);
        }
    }

    public static boolean isOpen(IncidentStatus status) {
        return status != IncidentStatus.RESOLVED && status != IncidentStatus.CLOSED;
    }

    public static VehicleTrip requireTrip(Incident incident) {
        VehicleTrip trip = incident.getVehicleTrip();
        if (trip == null && incident.getHorseLeg() != null) {
            trip = incident.getHorseLeg().getVehicleTrip();
        }
        if (trip == null) {
            throw new DispatchRuleException("incidentId", "Sự cố không gắn lượt xe.", true);
        }
        return trip;
    }

    public static boolean isApprovedExecution(IncidentStatus status, IncidentPlan latest, IncidentResolutionType type) {
        return status == IncidentStatus.PLAN_ACTIVE && latest != null
                && latest.getStatus() == IncidentPlanStatus.APPROVED && latest.getResolutionType() == type;
    }

    public static void requireApprovedExecution(IncidentStatus status, IncidentPlan latest, IncidentResolutionType type) {
        if (latest != null && latest.getResolutionType() == type && latest.getStatus() == IncidentPlanStatus.DONE) {
            String message = type == IncidentResolutionType.TRANSFER_VEHICLE
                    ? "Phương án đổi xe đã được thực hiện." : "Phương án đã được thực hiện.";
            throw new DispatchRuleException("incidentId", message, true);
        }
        if (!isApprovedExecution(status, latest, type)) {
            throw new DispatchRuleException("incidentId", "Sự cố chưa có phương án " + type + " đã được quản lý duyệt.", true);
        }
    }

    public static boolean requiresExecution(IncidentResolutionType type) {
        return type == IncidentResolutionType.VET_TREATMENT || type == IncidentResolutionType.STABLE_NEARBY
                || type == IncidentResolutionType.CANCEL;
    }

    public static void requireExecutedBeforeResolve(List<IncidentPlan> versions) {
        if (versions.isEmpty()) return;
        IncidentPlan latest = versions.getLast();
        if (versions.stream().anyMatch(plan -> requiresExecution(plan.getResolutionType())
                && (plan.getStatus() == IncidentPlanStatus.APPROVED || plan == latest && plan.getStatus() != IncidentPlanStatus.DONE))) {
            throw new DispatchRuleException("status", "Cần thực hiện phương án sự cố trước khi báo đã xử lý xong.", true);
        }
    }

    // Lượt xe đang có sự cố chưa đóng thì không báo sự cố mới cho cả lượt (mục 7.1)
    public static void requireNoOpenTripIncident(boolean openIncidentOnTrip) {
        if (openIncidentOnTrip) {
            throw new DispatchRuleException("vehicleTripId", "Lượt xe đang có sự cố chưa đóng.", true);
        }
    }

    // Quy tắc #13: vai trò bên xử lý cần có theo loại xử lý; không cần bên xử lý thì empty
    public static Optional<PartyRoleType> handlerRoleFor(IncidentResolutionType type) {
        return switch (type) {
            case VET_TREATMENT -> Optional.of(PartyRoleType.VET);
            case STABLE_NEARBY -> Optional.of(PartyRoleType.STABLE);
            case TRANSFER_VEHICLE, RETURN_TO_HUB -> Optional.of(PartyRoleType.RESCUE);
            case CONTINUE, CANCEL -> Optional.empty();
        };
    }

    // Thú y và gửi chuồng bắt buộc có bên xử lý; bên xử lý (nếu có) phải có vai trò còn hiệu lực đúng loại
    public static void requireHandler(IncidentResolutionType type, boolean hasHandler, Collection<PartyRoleType> handlerRoles) {
        Optional<PartyRoleType> needed = handlerRoleFor(type);
        boolean required = type == IncidentResolutionType.VET_TREATMENT || type == IncidentResolutionType.STABLE_NEARBY;
        if (required && !hasHandler) {
            throw new DispatchRuleException("handlerPartyId", "Phương án " + type + " cần chọn bên xử lý.");
        }
        if (hasHandler && (needed.isEmpty() || !handlerRoles.contains(needed.get()))) {
            throw new DispatchRuleException("handlerPartyId", "Bên xử lý không có vai trò phù hợp với phương án " + type + ".");
        }
    }

    public static void requireDecidable(IncidentPlanStatus status, boolean latestVersion) {
        if (status != IncidentPlanStatus.PROPOSED || !latestVersion) {
            throw new DispatchRuleException("status", "Chỉ duyệt hoặc từ chối phương án mới nhất đang chờ duyệt.", true);
        }
    }

    public static void requireRejectionReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new DispatchRuleException("reason", "Từ chối phương án phải ghi lý do.");
        }
    }

    public static void requireCanStartRest(IncidentStatus status, Instant restStartedAt, boolean fitConfirmed) {
        requireActivePlanForFit(status);
        if (restStartedAt != null) {
            throw new DispatchRuleException("restStartedAt", "Đã bắt đầu cho ngựa nghỉ.", true);
        }
        if (fitConfirmed) {
            throw new DispatchRuleException("fitConfirmedAt", "Đã xác nhận ngựa đủ sức đi tiếp.", true);
        }
    }

    public static Instant fitEligibleAt(Instant restStartedAt, int minRestMinutes) {
        return restStartedAt == null ? null : restStartedAt.plus(Duration.ofMinutes(minRestMinutes));
    }

    // Xác nhận ngựa đủ sức đi tiếp khi phương án đang hiệu lực và đã nghỉ, chăm sóc đủ thời gian tối thiểu
    public static void requireCanConfirmFit(IncidentStatus status, Instant restStartedAt, Instant now, int minRestMinutes) {
        requireActivePlanForFit(status);
        if (restStartedAt == null) {
            throw new DispatchRuleException("restStartedAt", "Chưa bắt đầu cho ngựa nghỉ.", true);
        }
        if (now.isBefore(fitEligibleAt(restStartedAt, minRestMinutes))) {
            throw new DispatchRuleException("status", "Ngựa chưa được nghỉ và chăm sóc đủ " + minRestMinutes + " phút.", true);
        }
    }

    private static void requireActivePlanForFit(IncidentStatus status) {
        if (status != IncidentStatus.PLAN_ACTIVE) {
            throw new DispatchRuleException("status", "Phương án xử lý chưa được duyệt.", true);
        }
    }

    // Đóng sự cố khi phương án đang hiệu lực; sự cố về ngựa phải có xác nhận đủ sức khỏe
    public static void requireResolvable(IncidentStatus status, boolean concernsHorse, boolean fitConfirmed) {
        if (status != IncidentStatus.PLAN_ACTIVE) {
            throw new DispatchRuleException("status", "Sự cố chưa có phương án đang hiệu lực.", true);
        }
        if (concernsHorse && !fitConfirmed) {
            throw new DispatchRuleException("status", "Chưa xác nhận ngựa đủ sức khỏe đi tiếp.", true);
        }
    }
}
