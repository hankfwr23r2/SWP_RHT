package com.example.racehorse_transport.dto.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Booking;
import org.junit.jupiter.api.Test;

class RatingDtoTest {

    @Test
    void requestCopiesOnlyScoreAndCommentAndResponseReadsGeneratedFields() {
        var booking = new Booking();
        booking.setId(17L);
        var rating = new RatingRequestDto((short) 5, "  Đã nhận ngựa  ").toEntity(booking);

        assertThat(rating.getBooking()).isSameAs(booking);
        assertThat(rating.getBookingId()).isNull();
        assertThat(rating.getRatedAt()).isNull();
        assertThat(rating.getComment()).isEqualTo("Đã nhận ngựa");
        rating.setBookingId(17L);
        assertThat(RatingResponseDto.from(rating).score()).isEqualTo((short) 5);
        assertThat(RatingResponseDto.from(rating).bookingId()).isEqualTo(17L);
    }
}
