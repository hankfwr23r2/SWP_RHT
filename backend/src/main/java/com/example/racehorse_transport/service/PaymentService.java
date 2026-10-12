package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.payment.*;
import com.example.racehorse_transport.entity.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentService {
    // CRUD cơ bản
    List<Payment> findAll();
    Optional<Payment> findById(Integer id);
    Payment save(Payment entity);
    void deleteById(Integer id);

    // =========================================================================
    // NGHIỆP VỤ TÀI CHÍNH DEV 2 (Flow 1, 4, 6, 8)
    // =========================================================================

    /**
     * Khách hàng đặt cọc 30% để nhận Vận đơn (Waybill)
     */
    PaymentResponse payDeposit(ProcessPaymentRequest request, String username);

    /**
     * Khách hàng thanh toán 70% còn lại trước giờ khởi hành (ngày D - Flow 4)
     */
    PaymentResponse payBalance(ProcessPaymentRequest request, String username);

    /**
     * Hủy đơn và xử lý hoàn cọc / phạt cọc theo quy tắc thời gian (PRD mục 8.2 & 8.3)
     */
    CancelWithRefundResponse cancelWithRefundPolicy(String bookingIdentifier, CancelBookingRequest request, String username);

    /**
     * Xem tóm tắt tài chính (tổng tiền, đã cọc, còn thiếu, trạng thái đã thanh toán đủ chưa)
     */
    PaymentSummaryResponse getPaymentSummary(String bookingIdentifier);

    /**
     * Lấy danh sách lịch sử tất cả các đợt thanh toán của một đơn hàng
     */
    List<PaymentResponse> getPaymentsByBooking(String bookingIdentifier);

    /**
     * Lấy chi tiết một giao dịch thanh toán
     */
    PaymentResponse getPaymentDetail(Integer paymentId);
}
