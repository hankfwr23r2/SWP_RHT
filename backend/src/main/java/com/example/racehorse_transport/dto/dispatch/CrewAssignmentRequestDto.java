package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.CrewRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CrewAssignmentRequestDto(
        @NotNull @Positive Long staffId,
        @NotNull CrewRole crewRole,
        @Size(max = 500) String note) {

    // Trạng thái do service đặt
    public CrewAssignment toEntity(VehicleTrip trip, Staff staff) {
        CrewAssignment assignment = new CrewAssignment();
        assignment.setVehicleTrip(trip);
        assignment.setStaff(staff);
        assignment.setCrewRole(crewRole);
        assignment.setNote(note == null || note.isBlank() ? null : note.strip());
        return assignment;
    }
}
