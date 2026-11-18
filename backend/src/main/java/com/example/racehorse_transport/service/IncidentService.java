package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.incident.*;

import java.util.List;

public interface IncidentService {

    IncidentResponseDto reportIncident(ReportIncidentRequestDto request, String actor, String role);

    IncidentResponseDto proposeExternalPlan(Integer incidentId, ProposeExternalPlanRequestDto request, String actor);

    IncidentResponseDto approveIncidentPlan(Integer incidentId, ApproveIncidentPlanRequestDto request, String actor);

    IncidentResponseDto rejectIncidentPlan(Integer incidentId, RejectIncidentPlanRequestDto request, String actor);

    IncidentResponseDto addExpense(Integer incidentId, IncidentExpenseRequestDto request, String actor, String role);

    IncidentResponseDto deleteExpense(Integer incidentId, String expenseId, String actor, String role);

    IncidentResponseDto confirmFit(Integer incidentId, ConfirmIncidentFitRequestDto request, String actor, String role);

    IncidentResponseDto resumeJourney(Integer incidentId, String actor, String role);

    // Rời trạm SOS trước giờ nghỉ đã duyệt chỉ được khi kíp xe xác nhận (confirmEarlyLeave), kèm ghi chú nếu có
    IncidentResponseDto resumeJourney(Integer incidentId, String actor, String role, boolean confirmEarlyLeave, String note);

    IncidentResponseDto completeResolution(Integer incidentId, ResolveIncidentRequestDto request, String actor);

    List<IncidentResponseDto> getIncidentsByBooking(Integer bookingId, String requester, String role);

    List<IncidentResponseDto> getCustomerIncidents(String customerUsername);

    List<IncidentResponseDto> getAllIncidents(String status);

    // Sự cố của nhiều đơn trong một truy vấn (đơn đã được kiểm tra quyền ở nơi gọi)
    List<IncidentResponseDto> getIncidentsForBookings(java.util.Collection<Integer> bookingIds);
}
