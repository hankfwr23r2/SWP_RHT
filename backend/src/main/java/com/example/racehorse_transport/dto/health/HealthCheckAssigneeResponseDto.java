package com.example.racehorse_transport.dto.health;

import com.example.racehorse_transport.entity.Staff;

public record HealthCheckAssigneeResponseDto(Long id, String name) {
    public static HealthCheckAssigneeResponseDto from(Staff staff) {
        return new HealthCheckAssigneeResponseDto(staff.getUserId(), staff.getFullName());
    }
}
