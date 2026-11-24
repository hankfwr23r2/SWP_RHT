package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public final class PaymentPolicy {
    private static final Pattern REFERENCE = Pattern.compile("P([0-9a-z]+)T([0-9]+)S([0-9a-f]{32})");

    private PaymentPolicy() {}

    public record Session(Long paymentId, Instant createdAt) {}

    public static String reference(Long paymentId, Instant now, UUID nonce) {
        return "P" + Long.toString(paymentId, 36) + "T" + now.getEpochSecond() + "S"
                + nonce.toString().replace("-", "");
    }

    public static Session parseReference(String value) {
        if (value == null || value.length() > 100) {
            return null;
        }
        var matcher = REFERENCE.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return new Session(Long.parseLong(matcher.group(1), 36), Instant.ofEpochSecond(Long.parseLong(matcher.group(2))));
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            return null;
        }
    }

    // Link VNPAY quá hạn trả và quá thời gian chờ kết quả thì không còn là giao dịch đang chờ.
    // Link mở sau hạn trả số dư không kéo dài thời gian xe phải chờ.
    public static boolean awaitsGateway(Session session, Instant dueAt, Instant now, int expireMinutes, int graceMinutes) {
        return session != null && (dueAt == null || session.createdAt().isBefore(dueAt))
                && now.isBefore(session.createdAt().plus(Duration.ofMinutes((long) expireMinutes + graceMinutes)));
    }

    public static boolean isOpen(Payment payment, Booking booking, Instant now) {
        if (payment.getStatus() != PaymentStatus.PENDING || payment.getPaymentMethod() != PaymentMethod.VNPAY) {
            return false;
        }
        return switch (payment.getPaymentType()) {
            case DEPOSIT -> booking.getStatus() == BookingStatus.AWAITING_DEPOSIT
                    && payment.getQuotation().getStatus() == QuotationStatus.ACCEPTED
                    && payment.getDueAt() != null
                    && now.isBefore(DepositPaymentWindowPolicy.expiresAt(payment.getDueAt()));
            case BALANCE -> booking.getStatus() == BookingStatus.CONFIRMED
                    && payment.getQuotation().getStatus() == QuotationStatus.ACCEPTED;
            default -> false;
        };
    }

    public static boolean canApply(Payment payment, Booking booking, Session session, Instant paidAt,
                                   Instant receivedAt, int graceMinutes, BigDecimal amount) {
        if (payment.getStatus() != PaymentStatus.PENDING || payment.getPaymentMethod() != PaymentMethod.VNPAY
                || payment.getQuotation() == null || payment.getQuotation().getStatus() != QuotationStatus.ACCEPTED
                || !payment.getCurrencyCode().equals(payment.getQuotation().getCurrencyCode())
                || paidAt == null || paidAt.isBefore(session.createdAt()) || paidAt.isAfter(receivedAt)) {
            return false;
        }
        return switch (payment.getPaymentType()) {
            case DEPOSIT -> amount.compareTo(payment.getAmount()) == 0 && payment.getDueAt() != null
                    && booking.getStatus() == BookingStatus.AWAITING_DEPOSIT
                    && DepositPaymentWindowPolicy.canConfirm(payment.getDueAt(), session.createdAt(),
                            paidAt, receivedAt, graceMinutes);
            case BALANCE -> amount.compareTo(payment.getAmount()) >= 0 && booking.getStatus() != BookingStatus.CANCELLED;
            default -> false;
        };
    }

    public static BigDecimal outstanding(Quotation quotation, List<Payment> payments) {
        return outstanding(quotation, payments.stream()
                .filter(payment -> payment.getPaymentType() != PaymentType.EXTRA_CHARGE).toList(), BigDecimal.ZERO);
    }

    public static BigDecimal outstanding(Quotation quotation, List<Payment> payments, BigDecimal approvedCosts) {
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        for (Payment payment : payments) {
            if (payment.getStatus() != PaymentStatus.PAID) {
                continue;
            }
            if (!quotation.getCurrencyCode().equals(payment.getCurrencyCode())) {
                throw BookingRuleException.conflict("Khoản thanh toán dùng tiền tệ khác báo giá.");
            }
            if (payment.getPaymentType() == PaymentType.REFUND) {
                refunded = refunded.add(payment.getAmount());
            } else {
                paid = paid.add(payment.getAmount());
            }
        }
        return SettlementPolicy.outstanding(quotation.getTotalAmount(), approvedCosts, paid, refunded);
    }
}
