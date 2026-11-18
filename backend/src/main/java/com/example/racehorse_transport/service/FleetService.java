package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.fleet.CrewResponseDto;
import com.example.racehorse_transport.dto.fleet.VehicleResponseDto;
import com.example.racehorse_transport.dto.fleet.VehicleRequestDto;

import java.time.Instant;
import java.util.List;

public interface FleetService {
    List<VehicleResponseDto> getAllVehicles();
    List<VehicleResponseDto> getAvailableVehicles(Instant departDate, String tripType);
    VehicleResponseDto createVehicle(VehicleRequestDto request);
    VehicleResponseDto updateVehicle(String identifier, VehicleRequestDto request);
    void deleteVehicle(String identifier);
    List<CrewResponseDto> getAllCrew(String role);
    List<CrewResponseDto> getAvailableCrew(Instant departDate, String role);
    com.example.racehorse_transport.entity.Vehicle findVehicleByIdOrCode(String identifier);
    void validateVehicleAvailability(Integer vehicleId, Instant departDate, String tripType, Integer excludeBookingId);
    com.example.racehorse_transport.entity.Staff findStaffByIdOrCode(String identifier, String expectedRole);
    void validateCrewAvailability(Integer staffId, Instant departDate, String tripType, Integer excludeBookingId);
}
