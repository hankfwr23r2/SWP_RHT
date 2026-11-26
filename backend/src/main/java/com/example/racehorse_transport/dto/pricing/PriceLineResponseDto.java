package com.example.racehorse_transport.dto.pricing;

import com.example.racehorse_transport.entity.enums.BillingUnit;
import com.example.racehorse_transport.policy.PricingPolicy.Line;
import java.math.BigDecimal;

public record PriceLineResponseDto(Long priceItemId, String serviceCode, BillingUnit billingUnit,
                                   Long vehicleClassId, String stallTypeCode, BigDecimal quantity,
                                   BigDecimal unitPrice, BigDecimal amount, boolean passThrough) {
    public static PriceLineResponseDto from(Line line) {
        return new PriceLineResponseDto(line.priceItemId(), line.serviceCode(), line.billingUnit(),
                line.vehicleClassId(), line.stallTypeCode(), line.quantity(), line.unitPrice(),
                line.amount(), line.passThrough());
    }
}
