package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.CrewAssignmentRequestDto;
import com.example.racehorse_transport.dto.dispatch.MyCrewAssignmentResponseDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripResponseDto;
import java.util.List;

// Phân công tổ lái cho lượt xe và nhân viên xác nhận nhận việc. Chủ sở hữu crew_assignment: luồng B (B1).
public interface CrewAssignmentService {

    VehicleTripResponseDto assign(Long vehicleTripId, CrewAssignmentRequestDto request, String username);

    VehicleTripResponseDto release(Long assignmentId, String username);

    List<MyCrewAssignmentResponseDto> mine(String username);

    MyCrewAssignmentResponseDto acknowledge(Long assignmentId, String username);
}
