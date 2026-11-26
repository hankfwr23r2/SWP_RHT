package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.RatingRequestDto;
import com.example.racehorse_transport.dto.booking.RatingResponseDto;

public interface RatingService {
    RatingResponseDto rate(Long bookingId, String username, RatingRequestDto request);
}
