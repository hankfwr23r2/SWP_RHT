package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.CrewAssignmentRequestDto;
import com.example.racehorse_transport.dto.dispatch.CrewCandidateResponseDto;
import com.example.racehorse_transport.dto.dispatch.MyCrewAssignmentResponseDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripResponseDto;
import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.Location;
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
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.policy.TrackingPolicy;
import com.example.racehorse_transport.policy.TripSchedulePolicy;
import com.example.racehorse_transport.policy.TripSchedulePolicy.Rules;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.TreeMap;
import com.example.racehorse_transport.policy.TrackingPolicy.CustodyHolder;
import com.example.racehorse_transport.repository.HandoverHorseRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import java.util.HashMap;
import java.util.Map;
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
    private final HorseLegRepository legs;
    private final HandoverHorseRepository scans;
    private final TripStopRepository stops;
    private final SystemConfigService configs;

    @Override
    @Transactional
    public VehicleTripResponseDto assign(Long vehicleTripId, CrewAssignmentRequestDto request) {
        VehicleTrip trip = trips.findForUpdate(vehicleTripId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + vehicleTripId));
        // Lượt đang chạy chỉ nhận thêm hộ tống thay ca giữa đường (người thay tự tới điểm dừng rồi ký SHIFT)
        boolean midway = trip.getStatus() == VehicleTripStatus.IN_PROGRESS;
        if (!midway && trip.getStatus() != VehicleTripStatus.PLANNED && trip.getStatus() != VehicleTripStatus.DISPATCHED) {
            throw new DispatchRuleException("vehicleTripId", "Chỉ phân công cho lượt xe chưa chạy, hoặc thêm hộ tống thay ca cho lượt đang chạy.", true);
        }
        if (midway && request.crewRole() != CrewRole.ESCORT) {
            throw new DispatchRuleException("crewRole", "Lượt xe đang chạy chỉ thêm được hộ tống thay ca.", true);
        }
        Staff member = staff.findForUpdate(request.staffId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên " + request.staffId()));
        DispatchPolicy.requireActiveStaff(member.getEmploymentStatus());
        DispatchPolicy.requireCrewRole(request.crewRole(), member.getUser().getRole().getCode());
        if (assignments.existsByVehicleTrip_IdAndStaff_UserIdAndStatusNot(trip.getId(), member.getUserId(), CrewAssignmentStatus.RELEASED)) {
            throw new DispatchRuleException("staffId", "Nhân viên đã có trong lượt xe này.", true);
        }
        requireStaffFree(member, trip, midway);
        if (request.crewRole() == CrewRole.DRIVER) {
            requireDailyDriving(member, trip);
        }
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
        if (tripStatus == VehicleTripStatus.IN_PROGRESS) {
            // Quy tắc #38: không rời ca khi còn phụ trách ngựa chưa bàn giao
            List<String> held = horsesHeldBy(assignment.getStaff().getUserId(), assignment.getVehicleTrip().getId());
            if (!held.isEmpty()) {
                throw new DispatchRuleException("status", assignment.getStaff().getFullName() + " còn phụ trách " + String.join(", ", held)
                        + ". Người thay phải nhận bàn giao ca trước.", true);
            }
        } else if (tripStatus != VehicleTripStatus.PLANNED && tripStatus != VehicleTripStatus.DISPATCHED) {
            throw new DispatchRuleException("status", "Lượt xe đã kết thúc hoặc bị hủy, không gỡ nhân viên được.", true);
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

    // Quy tắc #37: người phụ trách một con là người ký lần giao nhận DONE mới nhất mà công ty là bên nhận
    private List<String> horsesHeldBy(Long userId, Long tripId) {
        List<HorseLeg> onboard = legs.findActiveOnTrip(tripId, Set.of(HorseLegStatus.CANCELLED, HorseLegStatus.FAILED)).stream()
                .filter(l -> l.getStatus() == HorseLegStatus.IN_PROGRESS).toList();
        if (onboard.isEmpty()) return List.of();
        Map<Long, Long> holder = new HashMap<>();
        scans.findByBookingHorseIds(onboard.stream().map(l -> l.getBookingHorse().getId()).toList()).stream()
                .filter(row -> row.getHandover().getStatus() == HandoverStatus.DONE && Boolean.TRUE.equals(row.getMicrochipMatched())
                        && TrackingPolicy.holderAfter(row.getHandover().getHandoverType(), row.getHandover().getToParty() != null) == CustodyHolder.COMPANY)
                .sorted(Comparator.comparing(row -> row.getHandover().getHandedAt()))
                .forEach(row -> holder.put(row.getHorseLeg().getBookingHorse().getId(), row.getHandover().getHandledBy().getUserId()));
        return onboard.stream().filter(l -> userId.equals(holder.get(l.getBookingHorse().getId())))
                .map(l -> l.getBookingHorse().getHorse().getName()).toList();
    }

    // Quy tắc #10: nhân viên không ở hai lượt trùng giờ và bắt đầu ở nơi lượt trước của mình kết thúc (chưa có lượt thì ở hub gốc).
    // Hộ tống thay ca giữa đường (midway) tự tới điểm dừng nên không xét nơi bắt đầu
    private void requireStaffFree(Staff member, VehicleTrip trip, boolean midway) {
        List<VehicleTrip> others = assignments.findActiveOfStaff(member.getUserId(), CrewAssignmentStatus.RELEASED, VehicleTripStatus.CANCELLED)
                .stream().map(CrewAssignment::getVehicleTrip).filter(t -> !t.getId().equals(trip.getId())).toList();
        DispatchPolicy.requireNoOverlap("staffId", "Nhân viên", new Interval(trip.getPlannedStartAt(), trip.getPlannedEndAt()),
                others.stream().map(t -> new Interval(t.getPlannedStartAt(), t.getPlannedEndAt())).toList());
        Location expectedStart = others.stream().filter(t -> !t.getPlannedEndAt().isAfter(trip.getPlannedStartAt()))
                .max(Comparator.comparing(VehicleTrip::getPlannedEndAt))
                .map(VehicleTrip::getEndLocation)
                .orElse(member.getHomeHubLocation());
        if (!midway) {
            DispatchPolicy.requireStartsWherePreviousEnded("staffId", "Nhân viên", trip.getStartLocation().getId(),
                    expectedStart == null ? null : expectedStart.getId(), expectedStart == null ? "chưa gán hub gốc" : expectedStart.getName());
        }
    }

    // Tài xế không lái quá giới hạn mỗi ngày, tính gộp mọi lượt; hai tài xế trên một xe thì mỗi người tính đủ
    private void requireDailyDriving(Staff member, VehicleTrip trip) {
        List<Long> tripIds = new ArrayList<>(assignments.findActiveOfStaff(member.getUserId(), CrewAssignmentStatus.RELEASED, VehicleTripStatus.CANCELLED)
                .stream().map(a -> a.getVehicleTrip().getId()).toList());
        tripIds.add(trip.getId());
        Map<Long, List<Interval>> planned = new TreeMap<>();
        stops.findByVehicleTrip_IdInOrderByVehicleTrip_IdAscSequenceNumberAsc(Set.copyOf(tripIds)).forEach(stop -> planned
                .computeIfAbsent(stop.getVehicleTrip().getId(), id -> new ArrayList<>())
                .add(new Interval(stop.getPlannedArrivalAt(), stop.getPlannedDepartureAt())));
        Rules rules = new Rules(configs.getInt("MAX_CONTINUOUS_HOURS"), configs.getInt("MIN_REST_MINUTES"), configs.getInt("MAX_DRIVE_HOURS_PER_DAY"), 0, 0);
        Map<LocalDate, Long> total = TripSchedulePolicy.drivingByDay(planned.values(), BUSINESS_ZONE, rules);
        // Chỉ xét những ngày lượt đang phân công có lái
        total.keySet().retainAll(TripSchedulePolicy.drivingByDay(List.of(planned.getOrDefault(trip.getId(), List.of())), BUSINESS_ZONE, rules).keySet());
        TripSchedulePolicy.requireDailyDriving("staffId", member.getFullName(), total, rules);
    }

    private CrewAssignment find(Long id) {
        return assignments.findWithTripAndStaff(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phân công " + id));
    }
}
