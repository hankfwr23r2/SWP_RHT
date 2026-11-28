package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.CrewCandidateResponseDto;
import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.CrewPositionPolicy;
import com.example.racehorse_transport.policy.DispatchPolicy;
import com.example.racehorse_transport.policy.DispatchPolicy.Interval;
import com.example.racehorse_transport.policy.TripSchedulePolicy;
import com.example.racehorse_transport.policy.TripSchedulePolicy.Rules;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.CrewPositionService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrewPositionServiceImpl implements CrewPositionService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final CrewAssignmentRepository assignments;
    private final StaffRepository staff;
    private final VehicleTripRepository trips;
    private final Clock clock;
    private final TripStopRepository stops;
    private final SystemConfigService configs;

    @Override
    public void requireSchedule(Staff member, CrewRole role, VehicleTrip trip, boolean midway) {
        requireSchedule(member, role, trip, midway, activeOf(member), null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<CrewAssignment> revalidateTrip(VehicleTrip trip, Interval previousWindow, List<Interval> stopTimes) {
        if (trip.getTripType() != VehicleTripType.LINEHAUL) {
            Rules rules = drivingRules();
            TripSchedulePolicy.requireDailyDriving("plannedStartAt", "Lượt xe",
                    TripSchedulePolicy.drivingByDay(List.of(stopTimes), BUSINESS_ZONE, rules), rules);
        }
        var crew = assignments.findByVehicleTrip_IdInOrderById(List.of(trip.getId())).stream()
                .filter(member -> member.getStatus() != CrewAssignmentStatus.RELEASED)
                .sorted(Comparator.comparing(member -> member.getStaff().getUserId())).toList();
        for (CrewAssignment assignment : crew) {
            Staff member = staff.findForUpdate(assignment.getStaff().getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên."));
            DispatchPolicy.requireActiveStaff(member.getEmploymentStatus());
            DispatchPolicy.requireCrewRole(assignment.getCrewRole(), member.getUser().getRole().getCode());
            requireSchedule(member, assignment.getCrewRole(), trip, false, activeOf(member), previousWindow);
        }
        return crew;
    }

    private List<CrewAssignment> activeOf(Staff member) {
        return assignments.findActiveOfStaff(member.getUserId(), CrewAssignmentStatus.RELEASED, VehicleTripStatus.CANCELLED);
    }

    private void requireSchedule(Staff member, CrewRole role, VehicleTrip trip, boolean midway,
                                 List<CrewAssignment> active, Interval previousWindow) {
        if (!midway) requireAtStart(member, trip, active);
        List<VehicleTrip> otherTrips = active.stream().map(CrewAssignment::getVehicleTrip)
                .filter(other -> !other.getId().equals(trip.getId())).toList();
        Interval window = interval(trip);
        DispatchPolicy.requireNoOverlap("staffId", "Nhân viên", window,
                otherTrips.stream().map(CrewPositionServiceImpl::interval).toList());
        if (previousWindow != null && !previousWindow.equals(window)) {
            var oldNext = DispatchPolicy.neighbors(otherTrips, previousWindow).next();
            var newNext = DispatchPolicy.neighbors(otherTrips, window).next();
            if (newNext != null) requireAtStart(member, newNext, active);
            if (oldNext != null && oldNext != newNext) requireAtStart(member, oldNext, active);
        }
        if (role == CrewRole.DRIVER) requireDailyDriving(member, trip, active);
    }

    private void requireAtStart(Staff member, VehicleTrip trip, List<CrewAssignment> active) {
        var position = CrewPositionPolicy.at(member.getHomeHubLocation(), active,
                trip.getId(), startOf(trip), clock.instant());
        Location here = position.location();
        Location start = trip.getStartLocation();
        if (here == null || !Objects.equals(here.getId(), start.getId())) {
            String where = position.tripCode() != null ? "trên xe " + position.tripCode()
                    : here == null ? "chưa xác định (chưa gán hub gốc)" : here.getName();
            throw new DispatchRuleException("staffId", "Nhân viên không ở điểm xuất phát. " + member.getFullName() + " dự kiến ở " + where
                    + ", lượt " + trip.getCode() + " xuất phát tại " + start.getName() + ".", true);
        }
    }

    @Override
    public List<CrewCandidateResponseDto> candidates(CrewRole role, Long tripId) {
        VehicleTrip trip = tripId == null ? null : trips.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + tripId));
        Instant at = trip == null || trip.getStatus() == VehicleTripStatus.IN_PROGRESS ? clock.instant() : startOf(trip);
        Long startId = trip == null ? null : trip.getStartLocation().getId();
        var people = staff.findAllByOrderByStaffCodeAsc().stream()
                .filter(s -> s.getEmploymentStatus() == EmploymentStatus.ACTIVE && s.getUser().getStatus() == AppUserStatus.ACTIVE)
                .filter(s -> role != CrewRole.VET && s.getUser().getRole().getCode().equals(role.name()))
                .toList();
        if (people.isEmpty()) {
            return List.of();
        }
        var history = assignments.findActiveOfStaffIds(people.stream().map(Staff::getUserId).toList(),
                CrewAssignmentStatus.RELEASED, VehicleTripStatus.CANCELLED).stream()
                .collect(Collectors.groupingBy(a -> a.getStaff().getUserId()));
        return people.stream().map(member -> {
                    var position = CrewPositionPolicy.at(member.getHomeHubLocation(),
                            history.getOrDefault(member.getUserId(), List.of()), tripId, at, clock.instant());
                    return CrewCandidateResponseDto.from(member, position.location(), position.tripCode());
                })
                .sorted(Comparator.comparing(candidate -> startId != null && startId.equals(candidate.positionLocationId())
                        ? 0 : 1))
                .toList();
    }

    // Tài xế không lái quá giới hạn mỗi ngày, tính gộp mọi lượt; hai tài xế trên một xe thì mỗi người tính đủ
    private void requireDailyDriving(Staff member, VehicleTrip trip, List<CrewAssignment> active) {
        List<Long> tripIds = new ArrayList<>(active.stream().map(a -> a.getVehicleTrip().getId()).toList());
        tripIds.add(trip.getId());
        Map<Long, List<Interval>> planned = new TreeMap<>();
        stops.findByVehicleTrip_IdInOrderByVehicleTrip_IdAscSequenceNumberAsc(Set.copyOf(tripIds)).forEach(stop -> planned
                .computeIfAbsent(stop.getVehicleTrip().getId(), id -> new ArrayList<>())
                .add(new Interval(stop.getPlannedArrivalAt(), stop.getPlannedDepartureAt())));
        Rules rules = drivingRules();
        Map<LocalDate, Long> total = TripSchedulePolicy.drivingByDay(planned.values(), BUSINESS_ZONE, rules);
        // Chỉ xét những ngày lượt đang phân công có lái
        total.keySet().retainAll(TripSchedulePolicy.drivingByDay(List.of(planned.getOrDefault(trip.getId(), List.of())), BUSINESS_ZONE, rules).keySet());
        TripSchedulePolicy.requireDailyDriving("staffId", member.getFullName(), total, rules);
    }

    private Rules drivingRules() {
        return new Rules(configs.getInt("MAX_CONTINUOUS_HOURS"), configs.getInt("MIN_REST_MINUTES"),
                configs.getInt("MAX_DRIVE_HOURS_PER_DAY"), 0, 0);
    }

    private static Interval interval(VehicleTrip trip) {
        return new Interval(trip.getPlannedStartAt(), trip.getPlannedEndAt());
    }

    // Lượt lẽ ra đã xuất phát thì xét theo hiện tại
    private Instant startOf(VehicleTrip trip) {
        Instant now = clock.instant();
        return trip.getPlannedStartAt().isBefore(now) ? now : trip.getPlannedStartAt();
    }
}
