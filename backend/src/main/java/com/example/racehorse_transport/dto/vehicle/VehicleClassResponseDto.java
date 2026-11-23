package com.example.racehorse_transport.dto.vehicle;

import com.example.racehorse_transport.entity.VehicleClass;

public record VehicleClassResponseDto(Long id, String code, String name, Integer maxStallCount) {

    public static VehicleClassResponseDto from(VehicleClass vehicleClass) {
        return new VehicleClassResponseDto(vehicleClass.getId(), vehicleClass.getCode(),
                vehicleClass.getName(), vehicleClass.getMaxStallCount());
    }
}
