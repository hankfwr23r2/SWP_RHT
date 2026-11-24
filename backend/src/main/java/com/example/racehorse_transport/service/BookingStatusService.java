package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.enums.BookingStatus;

// Hợp đồng gọi chéo giữa hai luồng (docs/KE_HOACH_TRIEN_KHAI.md mục 3.3).
// Cài đặt: luồng A (task A5). Gọi: luồng A, B. Chưa có cài đặt thì bên gọi test bằng mock.
public interface BookingStatusService {

    // Đổi booking.status qua BookingStatusPolicy và ghi một dòng booking_status_history trong cùng transaction.
    // actorUserId NULL: hệ thống tự đổi (scheduler).
    // Luồng B gọi khi handover PICKUP đầu tiên xong (IN_OPERATION) và khi chặng giao cuối cùng xong (DELIVERED).
    void transition(Long bookingId, BookingStatus toStatus, String reason, Long actorUserId);

    void recordInitialStatus(Booking booking);
}
