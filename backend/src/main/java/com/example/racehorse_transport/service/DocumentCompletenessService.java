package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.regulation.RegulationPreviewResponseDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.enums.DocumentSubmitter;
import java.util.Set;

// Hợp đồng gọi chéo giữa hai luồng (docs/KE_HOACH_TRIEN_KHAI.md mục 3.3).
// Cài đặt: luồng A (task A6). Gọi: luồng B. Chưa có cài đặt thì bên gọi test bằng mock.
public interface DocumentCompletenessService {

    Set<DocumentSubmitter> missingSubmitters(Long bookingId);

    RegulationPreviewResponseDto resolve(Booking booking);

    // Luồng B kiểm toàn bộ giấy bắt buộc khi xe xuất phát đón (GĐ5), khi nhận tại hub nếu SELF, hoặc khi nhận ở cửa khẩu với IMPORT.
    boolean isComplete(Long bookingId);

    // Khi sang QUOTING, chỉ kiểm giấy bắt buộc do CUSTOMER nộp.
    boolean isCompleteForQuoting(Long bookingId);
}
