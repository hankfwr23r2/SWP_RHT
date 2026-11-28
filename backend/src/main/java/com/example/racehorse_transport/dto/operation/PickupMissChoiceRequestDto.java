package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.entity.enums.PickupMissChoice;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public record PickupMissChoiceRequestDto(@NotNull PickupMissChoice choice, @Size(max = 500) String phoneNote,
        Instant hubArrivalAt, LocalDate pickupDate) {}
