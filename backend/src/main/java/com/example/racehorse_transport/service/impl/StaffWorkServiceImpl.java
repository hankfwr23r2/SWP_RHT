package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.staff.StaffWorkSummaryResponseDto;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.policy.StaffWorkPolicy;
import com.example.racehorse_transport.policy.TripReturnPolicy;
import com.example.racehorse_transport.repository.StaffWorkRepository;
import com.example.racehorse_transport.service.ScheduleChangeService;
import com.example.racehorse_transport.service.StaffWorkService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffWorkServiceImpl implements StaffWorkService {
    private final StaffWorkRepository work;
    private final ScheduleChangeService schedules;
    private final SystemConfigService config;
    private final Clock clock;

    @Override
    public StaffWorkSummaryResponseDto summarize(Collection<String> authorities) {
        if (authorities.contains("ROLE_MANAGER")) return manager();
        if (authorities.contains("ROLE_COORDINATOR")) return coordinator();
        return new StaffWorkSummaryResponseDto(Map.of(), Map.of());
    }

    private StaffWorkSummaryResponseDto manager() {
        var now = clock.instant();
        var cutoff = now.plus(config.getInt("DEPARTURE_DECISION_HOURS"), ChronoUnit.HOURS);
        var counts = work.countManagerWork(now, cutoff);
        var filters = StaffWorkPolicy.orderFilters(countsByStatus(), schedules.countOverdueDocuments());
        return new StaffWorkSummaryResponseDto(Map.of(
                "orders", StaffWorkPolicy.orderWork(filters), "assignments", counts.getAssignments(),
                "decisions", counts.getDecisions(), "refunds", counts.getRefunds(), "incidents", counts.getIncidents()), filters);
    }

    private StaffWorkSummaryResponseDto coordinator() {
        var counts = work.countCoordinatorWork(TripReturnPolicy.lateCutoff(clock.instant(), config.getInt("RETURN_LATE_MINUTES")));
        var statusCounts = countsByStatus();
        var filters = Arrays.stream(BookingStatus.values())
                .collect(Collectors.toMap(BookingStatus::name, status -> statusCounts.getOrDefault(status, 0L)));
        return new StaffWorkSummaryResponseDto(Map.of(
                "waiting", work.countWaitingForVehicle(), "crew", counts.getCrew(),
                "incidents", counts.getIncidents(), "late", counts.getLate()), filters);
    }

    private Map<BookingStatus, Long> countsByStatus() {
        return work.countBookingsByStatus().stream().collect(Collectors.toMap(
                StaffWorkRepository.StatusCount::getStatus, StaffWorkRepository.StatusCount::getTotal));
    }
}
