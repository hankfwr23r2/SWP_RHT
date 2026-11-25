package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.VehicleTripUpdateRequestDto;

public interface TripPlanEditService {
    void updateTrip(Long tripId, VehicleTripUpdateRequestDto request, String username);
}
