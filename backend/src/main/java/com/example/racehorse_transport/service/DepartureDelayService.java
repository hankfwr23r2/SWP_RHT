package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.departure.DepartureDelayRequestDto;
import com.example.racehorse_transport.dto.departure.DepartureResponseDto;
import com.example.racehorse_transport.policy.DepartureDelayPolicy.Thresholds;

public interface DepartureDelayService {
    Thresholds thresholds();
    DepartureResponseDto revise(Long departureId, DepartureDelayRequestDto request);
    void scan();
}
