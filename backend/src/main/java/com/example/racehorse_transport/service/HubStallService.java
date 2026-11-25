package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.health.HubStallChangeRequestDto;
import com.example.racehorse_transport.dto.health.HubStallCreateRequestDto;
import com.example.racehorse_transport.dto.health.HubStallInventoryResponseDto;
import com.example.racehorse_transport.dto.health.HubStallResponseDto;
import com.example.racehorse_transport.entity.HorseStay;

public interface HubStallService {
    HubStallInventoryResponseDto inventory(Long hubId);

    HubStallResponseDto create(Long hubId, HubStallCreateRequestDto request);

    HubStallResponseDto deactivate(Long hubId, Long stallId);

    HubStallResponseDto move(Long stayId, HubStallChangeRequestDto request);

    void allocateOnArrival(HorseStay stay);

    void allocateOnBalanceHold(HorseStay stay);

    void lockForTransition(HorseStay stay);
}
