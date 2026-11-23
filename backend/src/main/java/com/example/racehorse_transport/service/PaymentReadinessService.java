package com.example.racehorse_transport.service;

// Hợp đồng gọi chéo giữa hai luồng (docs/KE_HOACH_TRIEN_KHAI.md mục 3.3).
// Cài đặt: luồng A (task A7). Gọi: luồng B. Chưa có cài đặt thì bên gọi test bằng mock.
public interface PaymentReadinessService {

    // Đơn đã trả đủ tiền cần trả trước khi đón ngựa chưa. Luồng B kiểm khi xe xuất phát (GĐ5).
    boolean isPaidInFull(Long bookingId);
}
