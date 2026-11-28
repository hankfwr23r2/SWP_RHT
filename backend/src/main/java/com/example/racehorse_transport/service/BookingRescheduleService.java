package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingRescheduleRequestDto;
import com.example.racehorse_transport.dto.booking.BookingResponseDto;

// Đổi chuyến của đơn chưa cọc và nhả chỗ ở mốc quyết định. Đổi chuyến của đơn đã cọc: A8.
public interface BookingRescheduleService {

    // Khách chọn chuyến khác cho đơn SCHEDULE_CHANGE_REQUIRED chưa cọc hoặc đã cọc; đơn về UNDER_REVIEW hoặc CONFIRMED tương ứng
    BookingResponseDto reschedule(Long bookingId, String username, BookingRescheduleRequestDto request);

    // Đơn chưa báo giá tới mốc quyết định: nhả chỗ, sang SCHEDULE_CHANGE_REQUIRED. Trả về false nếu không còn phải nhả.
    boolean releaseAtDecision(Long bookingId);

    /** Đơn chạy thẳng theo định tuyến hub, không phải vì đang rời chuyến. */
    boolean isDirect(com.example.racehorse_transport.entity.Booking booking);

    /** Kiểm giấy phải làm lại và bảo hiểm cho ngày đón mới của đơn chạy thẳng. */
    void requireDirectSchedule(com.example.racehorse_transport.entity.Booking booking, java.time.LocalDate pickupDate);
}
