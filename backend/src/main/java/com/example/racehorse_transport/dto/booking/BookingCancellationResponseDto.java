package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import java.math.BigDecimal;
import java.util.List;

public record BookingCancellationResponseDto(Long bookingId, BookingStatus status, boolean allowed, String currencyCode,
        BigDecimal refundable, BigDecimal reviewable, BigDecimal forfeited, List<Long> reviewableQuotationLineIds) {

    public static BookingCancellationResponseDto from(Booking booking, boolean allowed, String currencyCode,
            BigDecimal refundable, BigDecimal reviewable, BigDecimal forfeited, List<Long> reviewableQuotationLineIds) {
        return new BookingCancellationResponseDto(booking.getId(), booking.getStatus(), allowed, currencyCode, refundable,
                reviewable, forfeited, reviewableQuotationLineIds);
    }
}
