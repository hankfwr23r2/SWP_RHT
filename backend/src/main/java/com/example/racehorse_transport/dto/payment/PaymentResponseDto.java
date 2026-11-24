package com.example.racehorse_transport.dto.payment;

import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponseDto(Long id, Long bookingId, Long quotationId, PaymentType type,
        PaymentStatus status, BigDecimal amount, String currencyCode, Instant dueAt, Instant paidAt) {

    public static PaymentResponseDto from(Payment payment) {
        return new PaymentResponseDto(payment.getId(), payment.getBooking().getId(),
                payment.getQuotation() == null ? null : payment.getQuotation().getId(), payment.getPaymentType(),
                payment.getStatus(), payment.getAmount(), payment.getCurrencyCode(), payment.getDueAt(), payment.getPaidAt());
    }
}
