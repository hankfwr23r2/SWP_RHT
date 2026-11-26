package com.example.racehorse_transport.dto.departure;

import java.util.List;

public record DepartureDetailResponseDto(DepartureResponseDto departure, List<DepartureVehicleResponseDto> vehicles) {
}
