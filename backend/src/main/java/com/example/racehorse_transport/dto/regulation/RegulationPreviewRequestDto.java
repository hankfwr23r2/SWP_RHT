package com.example.racehorse_transport.dto.regulation;

import com.example.racehorse_transport.entity.enums.BookingType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record RegulationPreviewRequestDto(
        @NotNull BookingType bookingType,
        @NotNull @Pattern(regexp = "[A-Z]{2}") String pickupCountryCode,
        @NotNull @Pattern(regexp = "[A-Z]{2}") String deliveryCountryCode,
        @NotNull LocalDate requestedPickupDate,
        @Positive Long gateLocationId,
        @Positive Long departureId) {
}
