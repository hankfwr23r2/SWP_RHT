package com.example.racehorse_transport.dto.complaint;

import com.example.racehorse_transport.dto.incident.EvidenceResponseDto;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.enums.ComplaintDecision;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import java.time.Instant;
import java.util.List;

public record ComplaintResponseDto(Long id, Long bookingHorseId, String description, IncidentStatus status,
        String reply, ComplaintDecision decision, String decisionReason, Instant reportedAt, Instant resolvedAt,
        List<EvidenceResponseDto> photos) {

    public static ComplaintResponseDto from(Incident complaint, List<EvidenceResponseDto> photos) {
        return new ComplaintResponseDto(complaint.getId(),
                complaint.getBookingHorse() == null ? null : complaint.getBookingHorse().getId(),
                complaint.getDescription(), complaint.getStatus(), complaint.getComplaintReply(),
                complaint.getComplaintDecision(), complaint.getComplaintDecisionReason(),
                complaint.getReportedAt(), complaint.getResolvedAt(), photos);
    }
}
