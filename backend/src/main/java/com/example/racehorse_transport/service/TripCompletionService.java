package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.operation.CrewTripResponseDto;
import com.example.racehorse_transport.dto.operation.TripCompleteRequestDto;

public interface TripCompletionService {
    CrewTripResponseDto complete(Long tripId, TripCompleteRequestDto request, String username);
}
