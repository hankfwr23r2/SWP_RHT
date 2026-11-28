package com.example.racehorse_transport.service;

import java.util.List;

// Hợp đồng gọi chéo giữa hai luồng (docs/KE_HOACH_TRIEN_KHAI.md mục 3.3).
// Cài đặt: luồng A (task A7, A8). Gọi: luồng A. Chưa có cài đặt thì bên gọi test bằng mock.
public interface PaymentService {

    // Lập khoản thu thêm (payment EXTRA_CHARGE) cho các chi phí đã duyệt mà khách hoặc người nhận chịu.
    // Trả về id của payment. Bên cài đặt gọi CostItemService.linkExtraPayment để gắn ngược.
    Long createExtraCharge(Long bookingId, List<Long> costItemIds, Long actorUserId);
}
