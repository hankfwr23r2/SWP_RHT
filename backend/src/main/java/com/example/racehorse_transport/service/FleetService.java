package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.fleet.CrewResponse;
import com.example.racehorse_transport.dto.fleet.VehicleResponse;

import java.time.Instant;
import java.util.List;

public interface FleetService {
    List<VehicleResponse> getAllVehicles();
    List<VehicleResponse> getAvailableVehicles(Instant departDate, String tripType);
    List<CrewResponse> getAllCrew(String role);
    List<CrewResponse> getAvailableCrew(Instant departDate, String role);
    com.example.racehorse_transport.entity.Vehicle findVehicleByIdOrCode(String identifier);
    void validateVehicleAvailability(Integer vehicleId, Instant departDate, String tripType, Integer excludeBookingId);
    com.example.racehorse_transport.entity.Staff findStaffByIdOrCode(String identifier, String expectedRole);
    void validateCrewAvailability(Integer staffId, Instant departDate, String tripType, Integer excludeBookingId);
}
