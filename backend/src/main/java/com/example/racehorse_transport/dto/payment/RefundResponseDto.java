package com.example.racehorse_transport.dto.payment;

import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import java.math.BigDecimal;
import java.time.Instant;

// Một khoản hoàn trên danh sách của quản lý
public record RefundResponseDto(Long id, Long bookingId, String bookingCode, String customerName, String customerPhone,
        PaymentType refundedPaymentType, BigDecimal amount, String currencyCode, PaymentStatus status, Instant paidAt) {

    public static RefundResponseDto from(Payment refund) {
        var booking = refund.getBooking();
        return new RefundResponseDto(refund.getId(), booking.getId(), booking.getCode(), booking.getCustomer().getFullName(),
                booking.getCustomer().getPhone(), refund.getRefundedPayment().getPaymentType(), refund.getAmount(),
                refund.getCurrencyCode(), refund.getStatus(), refund.getPaidAt());
    }
}
