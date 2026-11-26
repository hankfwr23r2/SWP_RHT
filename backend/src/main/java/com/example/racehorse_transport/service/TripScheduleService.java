package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.PlannedTripRequestDto;
import com.example.racehorse_transport.dto.dispatch.PlannedTripResponseDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripResponseDto;

// Tính giờ một lượt đón, giao, chạy thẳng theo đường thật, rồi lập lượt xe kèm ngựa trong một lần
public interface TripScheduleService {

    PlannedTripResponseDto plan(PlannedTripRequestDto request);

    VehicleTripResponseDto create(PlannedTripRequestDto request, String username);
}
