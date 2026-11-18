package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.payment.*;
import com.example.racehorse_transport.entity.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentService {
    void reconcileInsurance(String identifier, ReconcileInsuranceRequestDto request, String actor);
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
    CancelWithRefundResponseDto cancelWithRefundPolicy(String bookingIdentifier, CancelBookingRequestDto request, String username);

    // Quản lý hủy đơn "Không nhận được ngựa": khách mất phần cọc vận chuyển
    CancelWithRefundResponseDto cancelPickupFailed(String bookingIdentifier, String reason, String username);

    /**
     * Quản lý hủy chuyến đã đặt cọc, chưa rời điểm đón.
     * fault = COMPANY: công ty không thực hiện được chuyến, hoàn 100% số đã thu.
     * fault = CUSTOMER: lỗi do khách (không giao ngựa, không trả 70%...), khách mất phần cọc vận chuyển.
     */
    CancelWithRefundResponseDto cancelTrip(String bookingIdentifier, String fault, String reason, String username);

    /**
     * Xem tóm tắt tài chính (tổng tiền, đã cọc, còn thiếu, trạng thái đã thanh toán đủ chưa)
     */
    PaymentSummaryResponseDto getPaymentSummary(String bookingIdentifier, String customerUsername);

    /**
     * Lấy danh sách lịch sử tất cả các đợt thanh toán của một đơn hàng
     */
    List<PaymentResponseDto> getPaymentsByBooking(String bookingIdentifier, String customerUsername);

    /**
     * Lấy chi tiết một giao dịch thanh toán.
     * Ở các hàm xem: customerUsername khác null thì người gọi là khách và phải là chủ đơn; null là nhân viên.
     */
    PaymentResponseDto getPaymentDetail(Integer paymentId, String customerUsername);
}
