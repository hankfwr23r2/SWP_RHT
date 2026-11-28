package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.Location;

// Nhân viên có thể phân công vào lượt xe
public record CrewCandidateResponseDto(Long staffId, String staffCode, String fullName, String roleCode,
                                       Long homeHubLocationId, String homeHubName,
                                       Long positionLocationId, String positionLocationName, String positionTripCode) {

    public static CrewCandidateResponseDto from(Staff staff, Location position, String tripCode) {
        var hub = staff.getHomeHubLocation();
        return new CrewCandidateResponseDto(staff.getUserId(), staff.getStaffCode(), staff.getFullName(),
                staff.getUser().getRole().getCode(), hub == null ? null : hub.getId(), hub == null ? null : hub.getName(),
                position == null ? null : position.getId(), position == null ? null : position.getName(), tripCode);
    }
}
