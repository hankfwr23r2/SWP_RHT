package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.TripStop;

public interface TripReadinessService {
    void requireReadyToStart(VehicleTrip trip);

    void requireTripUnblocked(Long tripId);

    void requireBorderClearance(TripStop stop);
}
