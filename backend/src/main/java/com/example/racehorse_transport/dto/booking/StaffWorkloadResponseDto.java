package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.Staff;

public record StaffWorkloadResponseDto(Long staffId, String staffName, String role, long assignedCount, long lateTripCount, long openIncidentCount) {
    public static StaffWorkloadResponseDto from(Staff staff, long assignedCount) {
        return from(staff, assignedCount, 0, 0);
    }

    public static StaffWorkloadResponseDto from(Staff staff, long assignedCount, long lateTripCount, long openIncidentCount) {
        return new StaffWorkloadResponseDto(staff.getUserId(), staff.getFullName(),
                staff.getUser().getRole().getCode(), assignedCount, lateTripCount, openIncidentCount);
    }
}
