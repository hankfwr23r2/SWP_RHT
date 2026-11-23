package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.CrewAssignmentRequestDto;
import com.example.racehorse_transport.dto.dispatch.CrewCandidateResponseDto;
import com.example.racehorse_transport.dto.dispatch.MyCrewAssignmentResponseDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripResponseDto;
import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DispatchPolicy;
import com.example.racehorse_transport.policy.DispatchPolicy.Interval;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.CrewAssignmentService;
import com.example.racehorse_transport.service.DispatchService;
import com.example.racehorse_transport.service.NotificationService;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CrewAssignmentServiceImpl implements CrewAssignmentService {

    private static final Set<VehicleTripStatus> ENDED = Set.of(VehicleTripStatus.COMPLETED, VehicleTripStatus.CANCELLED);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM").withZone(BUSINESS_ZONE);

    private final CrewAssignmentRepository assignments;
    private final VehicleTripRepository trips;
    private final StaffRepository staff;
    private final DispatchService dispatch;
    private final NotificationService notifications;

    @Override
    @Transactional
    public VehicleTripResponseDto assign(Long vehicleTripId, CrewAssignmentRequestDto request) {
        VehicleTrip trip = trips.findForUpdate(vehicleTripId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + vehicleTripId));
        if (trip.getStatus() != VehicleTripStatus.PLANNED && trip.getStatus() != VehicleTripStatus.DISPATCHED) {
            throw new DispatchRuleException("vehicleTripId", "Chỉ phân công cho lượt xe chưa chạy.", true);
        }
        Staff member = staff.findForUpdate(request.staffId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên " + request.staffId()));
        DispatchPolicy.requireActiveStaff(member.getEmploymentStatus());
        DispatchPolicy.requireCrewRole(request.crewRole(), member.getUser().getRole().getCode());
        if (assignments.existsByVehicleTrip_IdAndStaff_UserIdAndStatusNot(trip.getId(), member.getUserId(), CrewAssignmentStatus.RELEASED)) {
            throw new DispatchRuleException("staffId", "Nhân viên đã có trong lượt xe này.", true);
        }
        requireStaffFree(member, trip);
        CrewAssignment assignment = request.toEntity(trip, member);
        assignment.setStatus(CrewAssignmentStatus.ASSIGNED);
        assignments.save(assignment);
        notifications.notify(List.of(member.getUserId()), null, "Lượt xe mới",
                "Bạn được phân công lượt xe " + trip.getCode() + " bắt đầu lúc " + TIME.format(trip.getPlannedStartAt())
                        + ". Mở \"Lượt xe của tôi\" để xác nhận nhận việc.", null);
        return dispatch.trip(trip.getId());
    }

    @Override
    @Transactional
    public VehicleTripResponseDto release(Long assignmentId) {
        CrewAssignment assignment = find(assignmentId);
        VehicleTripStatus tripStatus = assignment.getVehicleTrip().getStatus();
        if (tripStatus != VehicleTripStatus.PLANNED && tripStatus != VehicleTripStatus.DISPATCHED) {
            throw new DispatchRuleException("status", "Lượt xe đã chạy, không gỡ nhân viên được.", true);
        }
        assignment.setStatus(CrewAssignmentStatus.RELEASED);
        return dispatch.trip(assignment.getVehicleTrip().getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CrewCandidateResponseDto> candidates(CrewRole crewRole) {
        String roleCode = switch (crewRole) {
            case DRIVER -> "DRIVER";
            case ESCORT -> "ESCORT";
            case VET -> null;
        };
        return staff.findAllByOrderByStaffCodeAsc().stream()
                .filter(s -> s.getEmploymentStatus() == EmploymentStatus.ACTIVE && s.getUser().getStatus() == AppUserStatus.ACTIVE)
                .filter(s -> s.getUser().getRole().getCode().equals(roleCode))
                .map(CrewCandidateResponseDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyCrewAssignmentResponseDto> mine(String username) {
        return assignments.findOpenOfUser(username, CrewAssignmentStatus.RELEASED, ENDED).stream()
                .map(MyCrewAssignmentResponseDto::from).toList();
    }

    @Override
    @Transactional
    public MyCrewAssignmentResponseDto acknowledge(Long assignmentId, String username) {
        CrewAssignment assignment = find(assignmentId);
        if (!assignment.getStaff().getUser().getUsername().equals(username)) {
            throw new AccessDeniedException("Đây không phải phân công của bạn.");
        }
        if (assignment.getStatus() == CrewAssignmentStatus.RELEASED) {
            throw new DispatchRuleException("status", "Bạn đã được gỡ khỏi lượt xe này.", true);
        }
        if (ENDED.contains(assignment.getVehicleTrip().getStatus())) {
            throw new DispatchRuleException("status", "Lượt xe đã kết thúc hoặc bị hủy.", true);
        }
        assignment.setStatus(CrewAssignmentStatus.ACKNOWLEDGED);
        return MyCrewAssignmentResponseDto.from(assignment);
    }

    // Quy tắc #10: nhân viên không ở hai lượt trùng giờ và bắt đầu ở nơi lượt trước của mình kết thúc (chưa có lượt thì ở hub gốc)
    private void requireStaffFree(Staff member, VehicleTrip trip) {
        List<VehicleTrip> others = assignments.findActiveOfStaff(member.getUserId(), CrewAssignmentStatus.RELEASED, VehicleTripStatus.CANCELLED)
                .stream().map(CrewAssignment::getVehicleTrip).filter(t -> !t.getId().equals(trip.getId())).toList();
        DispatchPolicy.requireNoOverlap("staffId", "Nhân viên", new Interval(trip.getPlannedStartAt(), trip.getPlannedEndAt()),
                others.stream().map(t -> new Interval(t.getPlannedStartAt(), t.getPlannedEndAt())).toList());
        Long expectedStart = others.stream().filter(t -> !t.getPlannedEndAt().isAfter(trip.getPlannedStartAt()))
                .max(Comparator.comparing(VehicleTrip::getPlannedEndAt))
                .map(t -> t.getEndLocation().getId())
                .orElse(member.getHomeHubLocation() == null ? null : member.getHomeHubLocation().getId());
        DispatchPolicy.requireStartsWherePreviousEnded("staffId", "Nhân viên", trip.getStartLocation().getId(), expectedStart);
    }

    private CrewAssignment find(Long id) {
        return assignments.findWithTripAndStaff(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phân công " + id));
    }
}
