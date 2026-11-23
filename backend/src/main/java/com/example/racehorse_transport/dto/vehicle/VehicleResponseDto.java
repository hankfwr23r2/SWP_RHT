package com.example.racehorse_transport.dto.vehicle;

import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import java.time.LocalDate;

public record VehicleResponseDto(Long id, String licensePlate, Long vehicleClassId, String vehicleClassCode,
                                 Integer standardStallCount, Integer wideStallCount, Long homeHubLocationId,
                                 String homeHubLocationCode, LocalDate registrationExpiryDate, VehicleStatus status) {

    public static VehicleResponseDto from(Vehicle vehicle) {
        return new VehicleResponseDto(vehicle.getId(), vehicle.getLicensePlate(), vehicle.getVehicleClass().getId(),
                vehicle.getVehicleClass().getCode(), vehicle.getStandardStallCount(), vehicle.getWideStallCount(),
                vehicle.getHomeHubLocation().getId(), vehicle.getHomeHubLocation().getCode(),
                vehicle.getRegistrationExpiryDate(), vehicle.getStatus());
    }
}
