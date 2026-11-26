package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.IncidentPlan;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.enums.IncidentResolutionType;
import java.time.Instant;
import java.util.List;

public interface IncidentPlanService {

    Party handlerFor(IncidentResolutionType type, Long partyId);

    IncidentPlan lockApproved(Long incidentId, IncidentResolutionType type, Long expectedPlanId);

    IncidentPlan lockApprovedStablePlan(Long planId);

    List<IncidentPlan> approvedStablePlans(Long tripId);

    void discardStableDrafts(Long incidentId, Instant at);

    void discardOvernightDrafts(Incident incident, Instant at);
}
