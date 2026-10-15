package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.incident.*;

import java.util.List;

public interface IncidentService {

    IncidentResponse reportIncident(ReportIncidentRequest request, String actor, String role);

    IncidentResponse proposeExternalPlan(Integer incidentId, ProposeExternalPlanRequest request, String actor);

    IncidentResponse approveIncidentPlan(Integer incidentId, ApproveIncidentPlanRequest request, String actor);

    IncidentResponse rejectIncidentPlan(Integer incidentId, RejectIncidentPlanRequest request, String actor);

    IncidentResponse addExpense(Integer incidentId, IncidentExpenseRequest request, String actor, String role);

    IncidentResponse confirmFit(Integer incidentId, String actor, String role);

    IncidentResponse resumeJourney(Integer incidentId, String actor, String role);

    IncidentResponse completeResolution(Integer incidentId, ResolveIncidentRequest request, String actor);

    List<IncidentResponse> getIncidentsByBooking(Integer bookingId, String requester, String role);

    List<IncidentResponse> getAllIncidents(String status);
}
