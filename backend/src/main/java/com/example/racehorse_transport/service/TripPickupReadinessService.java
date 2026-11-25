package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.VehicleTrip;

public interface TripPickupReadinessService {
    void requireReady(VehicleTrip trip);
}
