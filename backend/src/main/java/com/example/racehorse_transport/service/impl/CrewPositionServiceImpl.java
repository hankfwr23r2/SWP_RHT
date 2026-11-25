package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.dispatch.CrewCandidateResponseDto;
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
import com.example.racehorse_transport.policy.CrewPositionPolicy;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.CrewPositionService;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrewPositionServiceImpl implements CrewPositionService {

    private final CrewAssignmentRepository assignments;
    private final StaffRepository staff;
    private final VehicleTripRepository trips;
    private final Clock clock;

    @Override
    public void requireAtStart(Staff member, VehicleTrip trip) {
        var position = CrewPositionPolicy.at(member.getHomeHubLocation(),
                assignments.findActiveOfStaff(member.getUserId(), CrewAssignmentStatus.RELEASED, VehicleTripStatus.CANCELLED),
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

    // Lượt lẽ ra đã xuất phát thì xét theo hiện tại
    private Instant startOf(VehicleTrip trip) {
        Instant now = clock.instant();
        return trip.getPlannedStartAt().isBefore(now) ? now : trip.getPlannedStartAt();
    }
}
