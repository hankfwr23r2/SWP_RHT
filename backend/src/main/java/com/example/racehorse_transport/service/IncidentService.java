package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.incident.*;

import java.util.List;

public interface IncidentService {

    IncidentResponse reportIncident(ReportIncidentRequest request, String actor, String role);

    IncidentResponse proposeExternalPlan(Integer incidentId, ProposeExternalPlanRequest request, String actor);

    IncidentResponse approveIncidentPlan(Integer incidentId, ApproveIncidentPlanRequest request, String actor);

    IncidentResponse rejectIncidentPlan(Integer incidentId, RejectIncidentPlanRequest request, String actor);

    IncidentResponse addExpense(Integer incidentId, IncidentExpenseRequest request, String actor, String role);

    IncidentResponse confirmFit(Integer incidentId, ConfirmIncidentFitRequest request, String actor, String role);

    IncidentResponse resumeJourney(Integer incidentId, String actor, String role);

    // Rời trạm SOS trước giờ nghỉ đã duyệt chỉ được khi kíp xe xác nhận (confirmEarlyLeave), kèm ghi chú nếu có
    IncidentResponse resumeJourney(Integer incidentId, String actor, String role, boolean confirmEarlyLeave, String note);

    IncidentResponse completeResolution(Integer incidentId, ResolveIncidentRequest request, String actor);

    List<IncidentResponse> getIncidentsByBooking(Integer bookingId, String requester, String role);

    List<IncidentResponse> getCustomerIncidents(String customerUsername);

    List<IncidentResponse> getAllIncidents(String status);

    // Sự cố của nhiều đơn trong một truy vấn (đơn đã được kiểm tra quyền ở nơi gọi)
    List<IncidentResponse> getIncidentsForBookings(java.util.Collection<Integer> bookingIds);
}
