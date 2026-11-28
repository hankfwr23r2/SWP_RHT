package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.incident.CostItemRequestDto;
import com.example.racehorse_transport.dto.incident.CostItemResponseDto;
import com.example.racehorse_transport.dto.incident.CostApproveRequestDto;
import java.util.List;

// Chi phí phát sinh (B4). Chủ sở hữu cost_item.
// linkExtraPayment là hợp đồng gọi chéo (docs/KE_HOACH_TRIEN_KHAI.md mục 3.3): luồng B cài đặt, luồng A gọi.
public interface CostItemService {

    List<CostItemResponseDto> ofIncident(Long incidentId);

    List<CostItemResponseDto> ofBooking(Long bookingId);

    // Ghi chi phí; bên chịu theo nguyên nhân sự cố (CostPayerPolicy), không gắn sự cố thì công ty chịu
    CostItemResponseDto record(CostItemRequestDto request, String username);

    // Quản lý duyệt; khoản khách chịu phải đã hỏi khách hoặc nằm trong hạn mức chi khẩn cấp của đơn
    CostItemResponseDto approve(Long costItemId, CostApproveRequestDto request, String username);

    List<CostItemResponseDto> customerCosts(Long bookingId, String username);

    CostItemResponseDto respond(Long costItemId, boolean agreed, String username);

    CostItemResponseDto recordPhoneConsent(Long costItemId, String note, String username);

    CostItemResponseDto markRecovered(Long costItemId);

    // Gắn khoản thu thêm vào các chi phí (cost_item.extra_charge_payment_id). Chỉ áp cho chi phí đã duyệt, không do công ty chịu.
    void linkExtraPayment(List<Long> costItemIds, Long paymentId);
}
