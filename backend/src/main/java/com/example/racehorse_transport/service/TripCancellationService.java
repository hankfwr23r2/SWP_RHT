package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.VehicleTripCancelRequestDto;

public interface TripCancellationService {
    com.example.racehorse_transport.dto.dispatch.VehicleTripResponseDto cancel(Long tripId, VehicleTripCancelRequestDto request, String username);
}
