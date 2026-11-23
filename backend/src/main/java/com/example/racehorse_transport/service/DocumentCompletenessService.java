package com.example.racehorse_transport.service;

// Hợp đồng gọi chéo giữa hai luồng (docs/KE_HOACH_TRIEN_KHAI.md mục 3.3).
// Cài đặt: luồng A (task A6). Gọi: luồng B. Chưa có cài đặt thì bên gọi test bằng mock.
public interface DocumentCompletenessService {

    com.example.racehorse_transport.dto.regulation.RegulationPreviewResponseDto resolve(
            com.example.racehorse_transport.entity.Booking booking);

    // Luồng B kiểm toàn bộ giấy bắt buộc khi xe xuất phát đón (GĐ5).
    boolean isComplete(Long bookingId);

    // Khi sang QUOTING, chỉ kiểm giấy bắt buộc do CUSTOMER nộp.
    boolean isCompleteForQuoting(Long bookingId);
}
