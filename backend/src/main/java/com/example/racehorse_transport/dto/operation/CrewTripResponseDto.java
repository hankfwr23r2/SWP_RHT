package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.dto.dispatch.TripHorseResponseDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripResponseDto;
import java.util.List;

// Lượt xe nhìn từ tổ xe: điểm dừng, ngựa trên lượt và các lần giao nhận đã lập
public record CrewTripResponseDto(VehicleTripResponseDto trip, List<TripHorseResponseDto> horses, List<HandoverResponseDto> handovers, List<Long> unpaidReturnStopIds) {}
