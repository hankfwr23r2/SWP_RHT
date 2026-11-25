package com.example.racehorse_transport.dto.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record DeliveryChangeConfirmRequestDto(@NotNull @Valid DeliveryChangeRequestDto selection,
        @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal expectedTotal) {}
