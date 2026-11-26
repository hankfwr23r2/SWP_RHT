package com.example.racehorse_transport.dto.dispatch;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record LinehaulApplyRequestDto(@NotEmpty List<@NotNull @Valid TruckChoiceDto> trucks) {
    public record TruckChoiceDto(@NotNull @Positive Long allotmentId, @Positive int number,
                                 @NotNull @Positive Long vehicleId, @NotNull List<@NotNull @Positive Long> bookingHorseIds) {}
}
