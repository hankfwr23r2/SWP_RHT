package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.util.Set;

public final class CoordinatorAssignmentPolicy {
    public static final Set<VehicleTripStatus> CLOSED_TRIPS = Set.of(VehicleTripStatus.COMPLETED, VehicleTripStatus.CANCELLED);
    public static final Set<IncidentStatus> CLOSED_INCIDENTS = Set.of(IncidentStatus.RESOLVED, IncidentStatus.CLOSED);

    private CoordinatorAssignmentPolicy() {}

    public static void requireAvailable(Staff staff) {
        if (!"COORDINATOR".equals(staff.getUser().getRole().getCode())
                || staff.getUser().getStatus() != AppUserStatus.ACTIVE
                || staff.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw new BookingRuleException("coordinatorId", "Điều phối phải có tài khoản hoạt động và đang làm việc.");
        }
    }

    public static void requireOwner(Staff owner, Long actorId) {
        if (owner != null && !owner.getUserId().equals(actorId)) {
            throw BookingRuleException.forbidden("Lượt xe do " + owner.getFullName() + " phụ trách.");
        }
    }

    public static boolean canTakeOver(VehicleTripStatus status, boolean hasOpenIncident) {
        return status == VehicleTripStatus.IN_PROGRESS || hasOpenIncident;
    }

    public static void requireTakeOver(VehicleTripStatus status, boolean hasOpenIncident) {
        if (!canTakeOver(status, hasOpenIncident)) {
            throw BookingRuleException.conflict("Chỉ nhận thay lượt đang chạy hoặc có sự cố chưa xử lý.");
        }
    }
}
