package com.example.racehorse_transport.service;

// Hợp đồng gọi chéo giữa hai luồng (docs/KE_HOACH_TRIEN_KHAI.md mục 3.3).
// Cài đặt: luồng A (task A5). Gọi: luồng B. Chưa có cài đặt thì bên gọi test bằng mock.
public interface BookingHorseService {

    // Loại một con ngựa khỏi đơn: ngựa không đạt khi nhận (GĐ6) hoặc trượt cách ly (mục 5.3).
    // Bên cài đặt nhả khoang của chuyến và lập báo giá phiên bản mới.
    void exclude(Long bookingHorseId, String reason, Long actorUserId);
}
