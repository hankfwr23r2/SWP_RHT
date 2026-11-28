package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.CrewRole;
import java.time.Instant;

public record CrewAssignmentResponseDto(
        Long id, Long staffId, String staffCode, String fullName, CrewRole crewRole, CrewAssignmentStatus status,
        Instant assignedAt, String note) {

    public static CrewAssignmentResponseDto from(CrewAssignment assignment) {
        var staff = assignment.getStaff();
        return new CrewAssignmentResponseDto(assignment.getId(), staff.getUserId(), staff.getStaffCode(), staff.getFullName(),
                assignment.getCrewRole(), assignment.getStatus(), assignment.getAssignedAt(), assignment.getNote());
    }
}
