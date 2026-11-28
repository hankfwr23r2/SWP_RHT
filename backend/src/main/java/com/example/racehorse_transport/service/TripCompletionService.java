package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.operation.CrewTripResponseDto;
import com.example.racehorse_transport.dto.operation.TripCompleteRequestDto;
import com.example.racehorse_transport.dto.operation.TripReturnPhotoRequestDto;

public interface TripCompletionService {
    CrewTripResponseDto complete(Long tripId, TripCompleteRequestDto request, String username);

    CrewTripResponseDto attachReturnPhoto(Long tripId, TripReturnPhotoRequestDto request, String username);
}
