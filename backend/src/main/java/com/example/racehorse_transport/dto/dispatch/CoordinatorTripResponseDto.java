package com.example.racehorse_transport.dto.dispatch;

public record CoordinatorTripResponseDto(VehicleTripResponseDto trip, String currentPoint, boolean late, int horseCount) {}
