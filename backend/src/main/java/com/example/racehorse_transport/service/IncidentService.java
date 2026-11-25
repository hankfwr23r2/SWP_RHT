package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.incident.PlanApproveRequestDto;
import com.example.racehorse_transport.dto.incident.IncidentPlanRequestDto;
import com.example.racehorse_transport.dto.incident.IncidentReportRequestDto;
import com.example.racehorse_transport.dto.incident.IncidentResponseDto;
import com.example.racehorse_transport.dto.incident.PlanRejectRequestDto;
import java.util.List;

// Sự cố và phương án xử lý có phiên bản (B4). Chủ sở hữu incident, incident_plan. Không đổi booking.status.
public interface IncidentService {

    List<IncidentResponseDto> open();

    List<IncidentResponseDto> ofTrip(Long vehicleTripId);

    IncidentResponseDto incident(Long id);

    // Tổ xe báo (người trong tổ lái); lượt đang có sự cố chưa đóng thì không báo sự cố mới cho cả lượt
    IncidentResponseDto report(IncidentReportRequestDto request, String username);

    // Điều phối lập phương án phiên bản mới; phương án đang chờ trước đó bị thay
    IncidentResponseDto propose(Long incidentId, IncidentPlanRequestDto request, String username);

    List<IncidentResponseDto.PlanDto> plansForCustomer(Long bookingId, String username);

    void acceptPlan(Long planId, String username);

    IncidentResponseDto approve(Long planId, PlanApproveRequestDto request, String username);

    // Từ chối phải có lý do; điều phối lập phiên bản tiếp theo
    IncidentResponseDto reject(Long planId, PlanRejectRequestDto request, String username);

    // Ngựa đủ sức đi tiếp, sau khi phương án được duyệt và đã nghỉ đủ MIN_REST_MINUTES
    IncidentResponseDto confirmFit(Long incidentId, String username);

    IncidentResponseDto resolve(Long incidentId, String username);

    IncidentResponseDto close(Long incidentId, String username);
}
