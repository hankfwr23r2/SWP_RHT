package com.example.racehorse_transport.service;

// Hợp đồng gọi chéo giữa hai luồng (docs/KE_HOACH_TRIEN_KHAI.md mục 3.3).
// Cài đặt: luồng A (task A7). Gọi: luồng B. Chưa có cài đặt thì bên gọi test bằng mock.
public interface PaymentReadinessService {

    // Luồng B kiểm đơn đã trả đủ trước khi xe rời nơi nhận ngựa (GĐ6).
    boolean isPaidInFull(Long bookingId);

    void lockBooking(Long bookingId);
}
