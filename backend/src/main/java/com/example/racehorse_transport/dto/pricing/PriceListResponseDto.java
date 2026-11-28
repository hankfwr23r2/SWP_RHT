package com.example.racehorse_transport.dto.pricing;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceListResponseDto(Long id, String name, String currencyCode, BigDecimal marginRate,
        BigDecimal fuelBufferRate, BigDecimal depositRate, LocalDate validFrom, LocalDate validTo, PriceListStatus status) {
    public static PriceListResponseDto from(PriceList list) {
        return new PriceListResponseDto(list.getId(), list.getName(), list.getCurrencyCode(), list.getMarginRate(),
                list.getFuelBufferRate(), list.getDepositRate(), list.getValidFrom(), list.getValidTo(), list.getStatus());
    }
}
