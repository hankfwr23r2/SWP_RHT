package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.booking.StaffWorkloadResponseDto;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.CoordinatorWorkloadService;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.racehorse_transport.policy.CoordinatorAssignmentPolicy.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoordinatorWorkloadServiceImpl implements CoordinatorWorkloadService {
    private final StaffRepository staff;
    private final VehicleTripRepository trips;
    private final DepartureRepository departures;
    private final IncidentRepository incidents;
    private final Clock clock;

    @Override
    public List<StaffWorkloadResponseDto> workloads() {
        var now = clock.instant();
        return staff.findActiveForRole("COORDINATOR").stream().map(person -> {
            Long id = person.getUserId();
            return StaffWorkloadResponseDto.from(person,
                    trips.countByCoordinator_UserIdAndStatusNotIn(id, CLOSED_TRIPS),
                    trips.countLate(id, CLOSED_TRIPS, now),
                    incidents.countOpenForCoordinator(id, CLOSED_INCIDENTS));
        }).toList();
    }

    @Override
    public void requireNoWork(Long staffId) {
        if (trips.countByCoordinator_UserIdAndStatusNotIn(staffId, CLOSED_TRIPS) > 0
                || departures.existsByCoordinator_UserIdAndStatusIn(staffId, Set.of(DepartureStatus.OPEN, DepartureStatus.CLOSED))) {
            throw BookingRuleException.conflict("Điều phối còn phụ trách lượt chưa kết thúc hoặc chuyến chưa chạy.");
        }
    }
}
