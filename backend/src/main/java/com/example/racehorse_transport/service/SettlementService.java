package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import com.example.racehorse_transport.dto.settlement.*;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;

import java.math.BigDecimal;

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

    /**
     * Chuẩn bị thanh toán quyết toán qua VNPAY: giữ tạm đánh giá, trả về số tiền phát sinh phải trả
     */
    BigDecimal holdForVnpay(Booking booking, RatingDto rating);

    /**
     * Áp hiệu ứng sau khi VNPAY xác nhận đã nhận tiền quyết toán: ghi đánh giá, đóng đơn, nhả xe
     */
    Booking confirmSettlementPaid(Payment payment, String actor);
}
