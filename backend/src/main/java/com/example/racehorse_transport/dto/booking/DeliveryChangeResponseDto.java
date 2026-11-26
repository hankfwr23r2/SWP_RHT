package com.example.racehorse_transport.dto.booking;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DeliveryChangeResponseDto(BigDecimal oldDeliveryCharge, BigDecimal newDeliveryCharge,
        BigDecimal difference, BigDecimal newTotal, BigDecimal newBalance, LocalDate estimatedDeliveryDate,
        String currencyCode) {}
