package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.Staff;

public record StaffWorkloadResponseDto(Long staffId, String staffName, String role, long assignedCount) {
    public static StaffWorkloadResponseDto from(Staff staff, long assignedCount) {
        return new StaffWorkloadResponseDto(staff.getUserId(), staff.getFullName(),
                staff.getUser().getRole().getCode(), assignedCount);
    }
}
