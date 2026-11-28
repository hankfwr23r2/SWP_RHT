package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.BookingRating;
import java.time.Instant;

public record RatingResponseDto(Long bookingId, Short score, String comment, Instant ratedAt) {

    public static RatingResponseDto from(BookingRating rating) {
        return new RatingResponseDto(rating.getBookingId(), rating.getScore(), rating.getComment(), rating.getRatedAt());
    }
}
