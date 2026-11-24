package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingRating;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RatingRequestDto(@NotNull @Min(1) @Max(5) Short score, @Size(max = 2000) String comment) {

    public BookingRating toEntity(Booking booking) {
        var rating = new BookingRating();
        rating.setBooking(booking);
        rating.setScore(score);
        rating.setComment(comment == null ? null : comment.strip());
        return rating;
    }
}
