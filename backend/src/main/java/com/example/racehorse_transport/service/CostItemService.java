package com.example.racehorse_transport.service;

import java.util.List;

// Hợp đồng gọi chéo giữa hai luồng (docs/KE_HOACH_TRIEN_KHAI.md mục 3.3).
// Cài đặt: luồng B (task B4). Gọi: luồng A. Chưa có cài đặt thì bên gọi test bằng mock.
public interface CostItemService {

    // Gắn khoản thu thêm vào các chi phí (cost_item.extra_payment_id). Chỉ áp cho chi phí không do công ty chịu.
    void linkExtraPayment(List<Long> costItemIds, Long paymentId);
}
