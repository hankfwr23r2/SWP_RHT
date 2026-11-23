package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;

public interface BookingService {

    BookingResponseDto submit(String username, String ipAddress, BookingCreateRequestDto request);
}
