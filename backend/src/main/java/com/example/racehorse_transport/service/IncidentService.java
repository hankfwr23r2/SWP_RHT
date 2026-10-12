package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.incident.*;

import java.util.List;

public interface IncidentService {

    IncidentResponse reportIncident(ReportIncidentRequest request);

    IncidentResponse proposeExternalPlan(Integer incidentId, ProposeExternalPlanRequest request);

    IncidentResponse approveIncidentPlan(Integer incidentId, ApproveIncidentPlanRequest request);

    IncidentResponse completeResolution(Integer incidentId, ResolveIncidentRequest request);

    List<IncidentResponse> getIncidentsByBooking(Integer bookingId);

    List<IncidentResponse> getAllIncidents(String status);
}
