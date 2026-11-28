package com.example.racehorse_transport.dto.health;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record StablingCollectionRequestDto(@NotNull Instant expectedAt) {}
