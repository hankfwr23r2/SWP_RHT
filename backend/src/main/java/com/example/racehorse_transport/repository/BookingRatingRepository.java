package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BookingRating;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BookingRatingRepository extends JpaRepository<BookingRating, Long> {

    List<BookingRating> findByBookingIdIn(Collection<Long> bookingIds);
}
