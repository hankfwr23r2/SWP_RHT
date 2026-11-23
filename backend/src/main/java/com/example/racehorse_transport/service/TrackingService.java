package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.tracking.BookingJourneyResponseDto;
import com.example.racehorse_transport.dto.tracking.PublicTrackingRequestDto;
import com.example.racehorse_transport.dto.tracking.PublicTrackingResponseDto;
import java.util.List;

// Hành trình từng con ngựa (B6): chặng, lưu trú, giao nhận, ngựa đang trong tay ai
public interface TrackingService {

    // Nhân viên xem mọi đơn
    BookingJourneyResponseDto bookingJourney(Long bookingId);

    // Khách chỉ xem đơn của mình
    BookingJourneyResponseDto customerBookingJourney(Long bookingId, String username);

    List<PublicTrackingResponseDto> publicTrack(PublicTrackingRequestDto request);
}
