package com.example.racehorse_transport.dto.payment;

import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.enums.DisputeDecision;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponseDto(Long id, Long bookingId, Long quotationId, PaymentType type,
        PaymentStatus status, BigDecimal amount, String currencyCode, Instant dueAt, Instant paidAt, Instant confirmationUntil,
        Instant issuedAt, Instant extendedAt, Instant disputedAt, String disputeReason, DisputeDecision disputeDecision,
        String disputeResponse, Instant writtenOffAt, Instant blockReleasedAt) {

    public static PaymentResponseDto from(Payment payment) {
        return from(payment, null);
    }

    // confirmationUntil: còn link VNPAY đang chờ kết quả tới mốc này, service tính
    public static PaymentResponseDto from(Payment payment, Instant confirmationUntil) {
        return new PaymentResponseDto(payment.getId(), payment.getBooking().getId(),
                payment.getQuotation() == null ? null : payment.getQuotation().getId(), payment.getPaymentType(),
                payment.getStatus(), payment.getAmount(), payment.getCurrencyCode(), payment.getDueAt(), payment.getPaidAt(),
                confirmationUntil, payment.getIssuedAt(), payment.getExtendedAt(), payment.getDisputedAt(),
                payment.getDisputeReason(), payment.getDisputeDecision(), payment.getDisputeResponse(),
                payment.getWrittenOffAt(), payment.getBlockReleasedAt());
    }
}
