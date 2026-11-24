package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingRescheduleRequestDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;

// Đổi chuyến của đơn chưa cọc và nhả chỗ ở mốc quyết định. Đổi chuyến của đơn đã cọc: A8.
public interface BookingRescheduleService {

    // Khách chọn chuyến khác cho đơn SCHEDULE_CHANGE_REQUIRED chưa cọc; đơn về UNDER_REVIEW
    BookingResponseDto reschedule(Long bookingId, String username, BookingRescheduleRequestDto request);

    // Đơn chưa báo giá tới mốc quyết định: nhả chỗ, sang SCHEDULE_CHANGE_REQUIRED. Trả về false nếu không còn phải nhả.
    boolean releaseAtDecision(Long bookingId);
}
