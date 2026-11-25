package com.example.racehorse_transport.dto.booking;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record InsurancePremiumRefundRequestDto(
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal refundedPremiumAmount,
        @NotBlank @Size(max = 120) String receiptReference) {}
