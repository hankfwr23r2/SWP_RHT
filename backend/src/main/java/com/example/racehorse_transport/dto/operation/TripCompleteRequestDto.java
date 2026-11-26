package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.VehicleEndCondition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TripCompleteRequestDto(
        @NotNull(message = "Chọn tình trạng xe.") VehicleEndCondition endVehicleCondition,
        @Size(max = 1000, message = "Ghi chú tình trạng xe tối đa 1000 ký tự.") String endVehicleNote) {

    public void applyTo(VehicleTrip trip) {
        trip.setEndVehicleCondition(endVehicleCondition);
        trip.setEndVehicleNote(endVehicleNote == null || endVehicleNote.isBlank() ? null : endVehicleNote.strip());
    }
}
