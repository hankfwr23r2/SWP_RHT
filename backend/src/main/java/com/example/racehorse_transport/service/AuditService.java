package com.example.racehorse_transport.service;

// Hợp đồng gọi chéo giữa hai luồng (docs/KE_HOACH_TRIEN_KHAI.md mục 3.3).
// Cài đặt: luồng B (task P0.3-B). Gọi: luồng A, B. Chưa có cài đặt thì bên gọi test bằng mock.
public interface AuditService {

    // Ghi nhật ký kiểm toán (audit_log). actorUserId NULL nghĩa là hệ thống tự chạy (scheduler).
    // oldData, newData được chụp lại dạng JSON (ngoại lệ được phép dùng JSON).
    void record(Long actorUserId, String action, String targetTable, Long targetId, Object oldData, Object newData);
}
