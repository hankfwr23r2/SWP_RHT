package com.example.racehorse_transport.service;

// Hợp đồng gọi chéo giữa hai luồng (docs/KE_HOACH_TRIEN_KHAI.md mục 3.3).
// Cài đặt: luồng A (task A6). Gọi: luồng B. Chưa có cài đặt thì bên gọi test bằng mock.
public interface DocumentCompletenessService {

    // Đơn đã đủ giấy bắt buộc theo country_rule và document_requirement chưa. Luồng B kiểm khi xe xuất phát (GĐ5).
    boolean isComplete(Long bookingId);
}
