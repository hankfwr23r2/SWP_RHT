package com.example.racehorse_transport.dto.payment;

import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.enums.PaymentType;
import java.math.BigDecimal;

// needsReview: đã nhận tiền nhưng chưa ghi nhận được vào đơn, quản lý đối soát và hoàn. Khách không trả lại.
public record PaymentResultResponseDto(boolean success, boolean needsReview, String code, String message,
        String bookingCode, PaymentType paymentType, BigDecimal amount, String transactionNumber) {

    public static PaymentResultResponseDto unverified(String code, String message) {
        return new PaymentResultResponseDto(false, false, code, message, null, null, null, null);
    }

    public static PaymentResultResponseDto from(Payment payment, boolean success, boolean needsReview, String code,
                                                String message, BigDecimal amount, String transactionNumber) {
        return new PaymentResultResponseDto(success, needsReview, code, message, payment.getBooking().getCode(),
                payment.getPaymentType(), amount, transactionNumber);
    }
}
