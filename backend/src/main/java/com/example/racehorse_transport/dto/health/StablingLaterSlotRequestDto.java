package com.example.racehorse_transport.dto.health;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record StablingLaterSlotRequestDto(@NotNull Instant earliestAt, @NotNull Instant chosenAt) {}
