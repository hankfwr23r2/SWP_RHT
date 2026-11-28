package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.CoordinatorTripHorseResponseDto;
import com.example.racehorse_transport.dto.dispatch.CoordinatorTripResponseDto;
import java.util.List;

public interface CoordinatorTripService {
    List<CoordinatorTripResponseDto> mine(String username);
    List<CoordinatorTripHorseResponseDto> horses(Long tripId, String username);
}
