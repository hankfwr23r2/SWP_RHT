package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingResponseDto;

public interface BookingReviewService {
    BookingResponseDto intake(Long id, String username);
    BookingResponseDto returnToCustomer(Long id, String username, String reason);
    BookingResponseDto resubmit(Long id, String username);
    BookingResponseDto reject(Long id, String username, String reason);
    BookingResponseDto changeDeparture(Long id, Long departureId);
    // Kiểm giấy bắt buộc của khách và hiệu lực bảo hiểm hiện tại trước khi sang QUOTING.
    BookingResponseDto reopenExpiredQuote(Long id, String username);

    BookingResponseDto startQuoting(Long id, String username);
}
