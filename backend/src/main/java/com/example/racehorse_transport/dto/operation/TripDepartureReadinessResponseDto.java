package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.entity.TripStop;

public record TripDepartureReadinessResponseDto(Long stopId, boolean waitingForEscort) {
    public static TripDepartureReadinessResponseDto from(TripStop stop, boolean waitingForEscort) {
        return new TripDepartureReadinessResponseDto(stop.getId(), waitingForEscort);
    }
}
