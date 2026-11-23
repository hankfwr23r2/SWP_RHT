package com.example.racehorse_transport.dto.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public record PricingPreviewRequestDto(
        @NotNull @Positive Long pickupAddressId,
        @NotNull @Positive Long deliveryAddressId,
        @Positive Long departureId,
        @NotNull Instant pickupAt,
        @NotEmpty List<@NotNull @Valid HorseSelection> horses) {

    public record HorseSelection(@NotNull @Positive Long horseId,
                                 @NotBlank @Size(max = 30) String stallTypeCode,
                                 @NotNull List<@NotNull @Positive Long> addOnServiceIds) {}
}
