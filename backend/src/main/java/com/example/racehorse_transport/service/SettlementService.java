package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.settlement.*;

public interface SettlementService {

    /**
     * 1. Kê khai chi phí phát sinh thực tế có chứng từ (Evidence-First - Flow 6, bước 2)
     */
    BookingResponse submitExpenses(String bookingIdentifier, SubmitExpensesRequest request, String username);

    /**
     * 2. Quản lý (Manager) đối soát chứng từ và phát hành Bảng quyết toán phát sinh (Flow 6, bước 3)
     */
    SettlementResponse issueSettlement(String bookingIdentifier, IssueSettlementRequest request, String username);

    /**
     * 3. Khách hàng thanh toán phát sinh (nếu có) + gửi đánh giá -> Đóng đơn hàng (Flow 6, bước 4 & 5)
     */
    BookingResponse payAndComplete(String bookingIdentifier, PaySettlementRequest request, String username);

    /**
     * 4. Xem chi tiết Bảng quyết toán phát sinh của đơn hàng
     */
    SettlementResponse getSettlementDetail(String bookingIdentifier);
}
