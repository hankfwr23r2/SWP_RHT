package com.example.racehorse_transport.dto.operation;

import jakarta.validation.constraints.Size;

public record TripStartRequestDto(@Size(max = 500) String earlyReason) {}
