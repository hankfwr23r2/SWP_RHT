package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.service.CoordinatorAssignmentService;
import com.example.racehorse_transport.dto.incident.IncidentResponseDto;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.IncidentPlanStatus;
import com.example.racehorse_transport.entity.enums.IncidentResolutionType;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.IncidentPolicy;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.HandoverService;
import com.example.racehorse_transport.service.HorseLegService;
import com.example.racehorse_transport.service.IncidentPlanService;
import com.example.racehorse_transport.service.IncidentReturnService;
import com.example.racehorse_transport.service.IncidentService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentReturnServiceImpl implements IncidentReturnService {

    private final IncidentPlanService plans;
    private final IncidentService incidentViews;
    private final CoordinatorAssignmentService coordinators;
    private final TripStopRepository stops;
    private final HorseLegRepository legs;
    private final HorseLegService horseLegs;
    private final HandoverService handovers;
    private final StaffRepository staff;
    private final AuditService audit;
    private final Clock clock;

    @Override
    @Transactional
    public IncidentResponseDto returnHorses(Long incidentId, IncidentResolutionType type, Long planId, String username) {
        var plan = plans.lockApproved(incidentId, type, planId);
        var incident = plan.getIncident();
        Long tripId = IncidentPolicy.requireTrip(incident).getId();
        var trip = coordinators.requireTripWrite(tripId, username);
        TripExecutionPolicy.requireInProgress(trip.getStatus());
        List<HorseLeg> onBoard = legs.findOnBoardForIncident(tripId, Set.of(HorseLegStatus.IN_PROGRESS)).stream()
                .filter(leg -> incident.getHorseLeg() == null || leg.getId().equals(incident.getHorseLeg().getId()))
                .toList();
        if (onBoard.isEmpty()) {
            throw new DispatchRuleException("incidentId", "Không có con ngựa nào đang trên xe để đưa về hub.", true);
        }
        int lastAlight = onBoard.stream().mapToInt(l -> l.getAlightTripStop().getSequenceNumber()).max().orElseThrow();
        boolean hubAhead = stops.findByVehicleTrip_IdOrderBySequenceNumber(tripId).stream()
                .anyMatch(s -> s.getStopType() == TripStopType.HUB && s.getActualArrivalAt() == null && s.getSequenceNumber() > lastAlight);
        if (!hubAhead) {
            throw new DispatchRuleException("incidentId", "Lượt xe không còn điểm hub phía sau các điểm giao để đưa ngựa về.", true);
        }
        Instant now = clock.instant();
        for (HorseLeg leg : onBoard) {
            leg.setStatus(HorseLegStatus.FAILED);
            leg.setActualEndAt(now);
        }
        if (type == IncidentResolutionType.CANCEL) {
            Long actor = staff.findByUser_Username(username)
                    .orElseThrow(() -> new AccessDeniedException("Không tìm thấy hồ sơ nhân viên.")).getUserId();
            List<Long> cancelled = horseLegs.cancelPlannedLegsAfter(onBoard);
            handovers.closeEmptyAlightingHandovers(onBoard, now);
            plan.setStatus(IncidentPlanStatus.DONE);
            audit.record(actor, "CANCEL_INCIDENT_LEGS", "incident", incidentId, null,
                    Map.of("incidentPlanId", plan.getId(), "failedLegIds", onBoard.stream().map(HorseLeg::getId).toList(),
                            "cancelledLegIds", cancelled));
        }
        legs.flush();
        return incidentViews.incident(incidentId);
    }
}
