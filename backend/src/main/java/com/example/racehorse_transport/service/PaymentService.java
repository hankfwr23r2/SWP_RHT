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
     * Khách hủy đơn của chính mình sau khi đặt cọc (mất cọc). Chỉ chủ đơn được hủy, và chỉ trước khi xe chở ngựa đi.
     */
    CancelWithRefundResponse cancelWithRefundPolicy(String bookingIdentifier, CancelBookingRequest request, String username);

    /**
     * Xem tóm tắt tài chính (tổng tiền, đã cọc, còn thiếu, trạng thái đã thanh toán đủ chưa)
     */
    PaymentSummaryResponse getPaymentSummary(String bookingIdentifier, String customerUsername);

    /**
     * Lấy danh sách lịch sử tất cả các đợt thanh toán của một đơn hàng
     */
    List<PaymentResponse> getPaymentsByBooking(String bookingIdentifier, String customerUsername);

    /**
     * Lấy chi tiết một giao dịch thanh toán.
     * Ở các hàm xem: customerUsername khác null thì người gọi là khách và phải là chủ đơn; null là nhân viên.
     */
    PaymentResponse getPaymentDetail(Integer paymentId, String customerUsername);
}
