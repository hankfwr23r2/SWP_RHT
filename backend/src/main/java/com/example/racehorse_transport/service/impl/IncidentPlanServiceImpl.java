package com.example.racehorse_transport.service.impl;

import static com.example.racehorse_transport.entity.enums.IncidentResolutionType.STABLE_NEARBY;

import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.IncidentPlan;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.IncidentResolutionType;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.IncidentPolicy;
import com.example.racehorse_transport.repository.HandoverRepository;
import com.example.racehorse_transport.repository.IncidentPlanRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.PartyRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.IncidentCostDecisionService;
import com.example.racehorse_transport.service.IncidentPlanService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentPlanServiceImpl implements IncidentPlanService {

    private final IncidentRepository incidents;
    private final IncidentPlanRepository plans;
    private final HandoverRepository handovers;
    private final PartyRepository parties;
    private final IncidentCostDecisionService decisions;
    private final VehicleTripRepository trips;

    @Override
    @Transactional(readOnly = true)
    public Party handlerFor(IncidentResolutionType type, Long partyId) {
        Party handler = partyId == null ? null : parties.findById(partyId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đối tác " + partyId));
        IncidentPolicy.requireHandler(type, handler != null, handler == null ? List.of() : decisions.validRoles(partyId));
        return handler;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public IncidentPlan lockApproved(Long incidentId, IncidentResolutionType type, Long expectedPlanId) {
        var incident = incidents.findForUpdate(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sự cố " + incidentId));
        IncidentPolicy.requireResolutionAvailable(incident.getIncidentType(), type);
        IncidentPlan latest = plans.findFirstByIncident_IdOrderByVersionDesc(incidentId).orElse(null);
        IncidentPolicy.requireApprovedExecution(incident.getStatus(), latest, type);
        if (expectedPlanId != null && !latest.getId().equals(expectedPlanId)) {
            throw new DispatchRuleException("incidentPlanId", "Chỉ thực hiện phiên bản phương án mới nhất đã được duyệt.", true);
        }
        return latest;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public IncidentPlan lockApprovedStablePlan(Long planId) {
        Long incidentId = plans.findIncidentId(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phương án " + planId));
        return lockApproved(incidentId, STABLE_NEARBY, planId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentPlan> approvedStablePlans(Long tripId) {
        return incidents.findForTrip(tripId).stream()
                .filter(incident -> incident.getStatus() == IncidentStatus.PLAN_ACTIVE)
                .flatMap(incident -> plans.findFirstByIncident_IdOrderByVersionDesc(incident.getId()).stream())
                .filter(plan -> IncidentPolicy.isApprovedExecution(IncidentStatus.PLAN_ACTIVE, plan, STABLE_NEARBY))
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void discardStableDrafts(Long incidentId, Instant at) {
        handovers.findByIncidentPlan_Incident_IdAndStatusOrderById(incidentId, HandoverStatus.PENDING)
                .forEach(handover -> discard(handover, at));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void discardOvernightDrafts(Incident incident, Instant at) {
        Long tripId = IncidentPolicy.requireTrip(incident).getId();
        trips.findForUpdate(tripId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + tripId));
        var affected = incident.getHorseLeg();
        for (Handover handover : handovers.findByTripStop_VehicleTrip_IdAndHandoverTypeAndStatusAndIncidentPlanIsNullOrderById(
                tripId, HandoverType.STAY_IN, HandoverStatus.PENDING)) {
            int atStop = handover.getTripStop().getSequenceNumber();
            if (affected == null || (affected.getBoardTripStop().getSequenceNumber() < atStop
                    && atStop < affected.getAlightTripStop().getSequenceNumber())) {
                discard(handover, at);
            }
        }
    }

    private static void discard(Handover handover, Instant at) {
        handover.setStatus(HandoverStatus.FAILED);
        handover.setHandedAt(at);
    }
}
