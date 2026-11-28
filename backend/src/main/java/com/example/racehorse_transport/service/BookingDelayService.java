package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingCancellationResponseDto;
import com.example.racehorse_transport.dto.booking.BookingDelayResponseDto;
import com.example.racehorse_transport.dto.booking.BookingRescheduleRequestDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;
import com.example.racehorse_transport.dto.booking.CancelBookingRequestDto;
import java.util.List;

public interface BookingDelayService {
    List<BookingDelayResponseDto> view(Long bookingId, String username);
    BookingResponseDto reschedule(Long bookingId, String username, BookingRescheduleRequestDto request);
    BookingCancellationResponseDto refundPreview(Long bookingId, String username);
    BookingCancellationResponseDto refund(Long bookingId, String username, CancelBookingRequestDto request);
}
