package com.example.racehorse_transport.dto.payment;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record RefundQuotationLinesRequestDto(@NotEmpty List<@NotNull @Positive Long> unspentQuotationLineIds) {}
