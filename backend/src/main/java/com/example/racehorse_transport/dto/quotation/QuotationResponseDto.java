package com.example.racehorse_transport.dto.quotation;

import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record QuotationResponseDto(Long id, Long bookingId, int version, QuotationStatus status, String currencyCode,
        LocalDate pickupDate, LocalDate departureDate, LocalDate estimatedDeliveryDate, Instant expiresAt,
        BigDecimal totalAmount, BigDecimal passThroughAmount, BigDecimal depositAmount, List<QuotationLineResponseDto> lines) {

    public static QuotationResponseDto from(Quotation quotation, List<QuotationLine> lines) {
        return new QuotationResponseDto(quotation.getId(), quotation.getBooking().getId(), quotation.getVersion(),
                quotation.getStatus(), quotation.getCurrencyCode(), quotation.getConfirmedPickupDate(),
                quotation.getConfirmedDepartureDate(), quotation.getEstimatedDeliveryDate(), quotation.getExpiresAt(),
                quotation.getTotalAmount(), quotation.getPassThroughAmount(), quotation.getDepositAmount(),
                lines.stream().map(QuotationLineResponseDto::from).toList());
    }
}
