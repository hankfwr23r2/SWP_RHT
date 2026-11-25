package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.CoordinatorAssignmentRequestDto;
import com.example.racehorse_transport.dto.dispatch.CoordinatorTakeOverRequestDto;
import com.example.racehorse_transport.entity.VehicleTrip;

public interface CoordinatorAssignmentService {
    void assignDeparture(Long id, CoordinatorAssignmentRequestDto request, String username);
    void assignTrip(Long id, CoordinatorAssignmentRequestDto request, String username);
    void takeOver(Long id, CoordinatorTakeOverRequestDto request, String username);
    VehicleTrip requireTripWrite(Long tripId, String username);

    VehicleTrip requireAssignedTripWrite(Long tripId, String username);
    void requireIncidentWrite(Long incidentId, String username);
    void requireCostWrite(Long costId, String username);
    boolean tripExists(Long tripId);
    long openIncidents(Long tripId);
    com.example.racehorse_transport.entity.Staff currentStaff(String username);
    java.util.List<Long> activeManagerIds();
    void assignAtCreation(VehicleTrip trip, String username);
}
