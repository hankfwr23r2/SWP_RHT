package com.example.racehorse_transport.dto.dispatch;

import java.time.Instant;

public record CoordinatorTripHorseResponseDto(TripHorseResponseDto horse, boolean boarded, boolean documentsComplete,
                                               Instant lastCheckAt, Instant nextCheckAt) {}
