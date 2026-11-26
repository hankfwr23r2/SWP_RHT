package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.VehicleTrip;

public interface TripReadinessService {
    void requireVehicleAndCrewAvailable(VehicleTrip trip);

    void requireReadyToStart(VehicleTrip trip);

    void requireTripUnblocked(Long tripId);
}
