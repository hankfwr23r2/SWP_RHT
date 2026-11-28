package com.example.racehorse_transport.dto.dispatch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Gán một con ngựa (dòng đơn) vào lượt xe: lên ở điểm dừng boardTripStopId, xuống ở alightTripStopId
public record HorseLegRequestDto(
        @NotNull @Positive Long bookingHorseId,
        @NotNull @Positive Long boardTripStopId,
        @NotNull @Positive Long alightTripStopId) {}
