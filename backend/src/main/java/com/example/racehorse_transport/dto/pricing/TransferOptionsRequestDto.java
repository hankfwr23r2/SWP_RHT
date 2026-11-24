package com.example.racehorse_transport.dto.pricing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

// Kiểm công ty chở được đầu đón, đầu giao hay không, trước bước xem giá
public record TransferOptionsRequestDto(
        @NotNull @Positive Long pickupAddressId,
        @NotNull @Positive Long deliveryAddressId,
        @NotNull Instant pickupAt) {}
