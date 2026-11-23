package com.example.racehorse_transport.service;

import java.util.List;

// Hợp đồng gọi chéo giữa hai luồng (docs/KE_HOACH_TRIEN_KHAI.md mục 3.3).
// Cài đặt: luồng B (task P0.3-B). Gọi: luồng A, B. Chưa có cài đặt thì bên gọi test bằng mock.
public interface NotificationService {

    // Tạo một thông báo và gửi tới danh sách người nhận (notification + notification_recipient).
    void notify(List<Long> recipientUserIds, Long bookingId, String title, String message, String link);
}
