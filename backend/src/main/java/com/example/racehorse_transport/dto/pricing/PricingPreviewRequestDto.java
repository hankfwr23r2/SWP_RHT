package com.example.racehorse_transport.dto.pricing;

import com.example.racehorse_transport.entity.enums.TransferMethod;
import com.example.racehorse_transport.entity.enums.TransportMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public record PricingPreviewRequestDto(
        @NotNull @Positive Long pickupAddressId,
        @NotNull @Positive Long deliveryAddressId,
        @Positive Long departureId,
        @NotNull Instant pickupAt,
        @NotEmpty List<@NotNull @Valid HorseSelection> horses,
        TransferMethod pickupMethod,
        TransferMethod deliveryMethod,
        TransportMode transportMode) {

    public PricingPreviewRequestDto(Long pickupAddressId, Long deliveryAddressId, Long departureId,
            Instant pickupAt, List<HorseSelection> horses, TransferMethod pickupMethod, TransferMethod deliveryMethod) {
        this(pickupAddressId, deliveryAddressId, departureId, pickupAt, horses, pickupMethod, deliveryMethod, TransportMode.SHARED);
    }

    public PricingPreviewRequestDto {
        transportMode = transportMode == null ? TransportMode.SHARED : transportMode;
        pickupMethod = pickupMethod == null ? TransferMethod.COMPANY : pickupMethod;
        deliveryMethod = deliveryMethod == null ? TransferMethod.COMPANY : deliveryMethod;
    }

    public PricingPreviewRequestDto(Long pickupAddressId, Long deliveryAddressId, Long departureId,
            Instant pickupAt, List<HorseSelection> horses) {
        this(pickupAddressId, deliveryAddressId, departureId, pickupAt, horses, TransferMethod.COMPANY, TransferMethod.COMPANY);
    }

    public record HorseSelection(@NotNull @Positive Long horseId,
                                 @NotBlank @Size(max = 30) String stallTypeCode,
                                 @NotNull List<@NotNull @Positive Long> addOnServiceIds,
                                 @Positive Long deliveryAddressId) {
        public HorseSelection(Long horseId, String stallTypeCode, List<Long> addOnServiceIds) {
            this(horseId, stallTypeCode, addOnServiceIds, null);
        }
    }
}
