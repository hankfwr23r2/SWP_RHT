package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.VehicleTrip;

public interface TripReadinessService {
    void requireReadyToStart(VehicleTrip trip);

    void requireTripUnblocked(Long tripId);
}
