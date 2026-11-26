package com.example.racehorse_transport.dto.departure;

import com.example.racehorse_transport.entity.DepartureVehicle;
import com.example.racehorse_transport.entity.enums.DepartureAllotmentType;

public record DepartureVehicleResponseDto(Long id, Long vehicleClassId, String vehicleClassCode,
                                          int vehicleCount, DepartureAllotmentType allotmentType, String reason) {
    public static DepartureVehicleResponseDto from(DepartureVehicle allotment) {
        return new DepartureVehicleResponseDto(allotment.getId(), allotment.getVehicleClass().getId(),
                allotment.getVehicleClass().getCode(), allotment.getVehicleCount(), allotment.getAllotmentType(),
                allotment.getReason());
    }
}
