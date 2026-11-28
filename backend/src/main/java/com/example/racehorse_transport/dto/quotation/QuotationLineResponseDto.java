package com.example.racehorse_transport.dto.quotation;

import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.enums.QuotationLineType;
import java.math.BigDecimal;

public record QuotationLineResponseDto(int lineNumber, QuotationLineType lineType, Long bookingHorseId,
        Long priceItemId, String description, BigDecimal quantity, String unit, BigDecimal unitPrice,
        BigDecimal amount, boolean passThrough) {

    public static QuotationLineResponseDto from(QuotationLine line) {
        return new QuotationLineResponseDto(line.getLineNumber(), line.getLineType(),
                line.getBookingHorse() == null ? null : line.getBookingHorse().getId(),
                line.getPriceItem() == null ? null : line.getPriceItem().getId(), line.getDescription(),
                line.getQuantity(), line.getUnit(), line.getUnitPrice(), line.getAmount(), line.getPassThrough());
    }
}
