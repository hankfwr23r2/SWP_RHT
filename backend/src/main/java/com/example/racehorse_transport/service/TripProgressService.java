package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.field.TripUpdateRequestDto;
import com.example.racehorse_transport.entity.Booking;

public interface TripProgressService {

    boolean isPickupComplete(Booking booking);

    BookingResponseDto updateTripProgress(String bookingIdentifier, TripUpdateRequestDto request,
            String username, String role);
}
