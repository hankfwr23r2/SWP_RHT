package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.dto.booking.BookingCancellationResponseDto;
import java.math.BigDecimal;

public record PickupMissPriceResponseDto(String currencyCode, BigDecimal total,
                                         BigDecimal balanceDue, BigDecimal refund, BigDecimal forfeited) {
    public static PickupMissPriceResponseDto from(Quotation quotation, BigDecimal balanceDue, BigDecimal refund) {
        return new PickupMissPriceResponseDto(quotation.getCurrencyCode(), quotation.getTotalAmount(),
                balanceDue, refund, BigDecimal.ZERO);
    }

    public static PickupMissPriceResponseDto fromCancellation(BookingCancellationResponseDto cancellation) {
        return new PickupMissPriceResponseDto(cancellation.currencyCode(), null, BigDecimal.ZERO,
                cancellation.refundable(), cancellation.forfeited());
    }
}
