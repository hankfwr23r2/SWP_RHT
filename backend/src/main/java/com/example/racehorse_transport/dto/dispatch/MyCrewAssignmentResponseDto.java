package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripType;
import java.time.Instant;

// Một dòng ở màn "Lượt xe của tôi" của tài xế, hộ tống
public record MyCrewAssignmentResponseDto(
        Long id, CrewRole crewRole, CrewAssignmentStatus status,
        Long vehicleTripId, String tripCode, VehicleTripType tripType, VehicleTripStatus tripStatus, String licensePlate,
        String startLocationName, String endLocationName, Instant plannedStartAt, Instant plannedEndAt) {

    public static MyCrewAssignmentResponseDto from(CrewAssignment assignment) {
        var trip = assignment.getVehicleTrip();
        return new MyCrewAssignmentResponseDto(assignment.getId(), assignment.getCrewRole(), assignment.getStatus(),
                trip.getId(), trip.getCode(), trip.getTripType(), trip.getStatus(), trip.getVehicle().getLicensePlate(),
                trip.getStartLocation().getName(), trip.getEndLocation().getName(), trip.getPlannedStartAt(), trip.getPlannedEndAt());
    }
}
