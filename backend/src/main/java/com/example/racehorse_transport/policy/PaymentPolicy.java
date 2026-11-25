package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    // Quy tắc của hệ thống cũ: link mở trước hạn trả chỉ được chờ kết quả thêm vài phút sau hạn, không gia hạn trả tiền.
    public static boolean awaitsGateway(Session session, Instant dueAt, Instant now, int graceMinutes) {
        return session != null && dueAt != null && session.createdAt().isBefore(dueAt)
                && now.isBefore(DepositPaymentWindowPolicy.confirmationUntil(dueAt, graceMinutes));
    }

    // Mốc chờ kết quả để hiện "Đang xác nhận thanh toán"; null khi không có link đang chờ
    public static Instant confirmationUntil(Payment payment, List<Session> pendingSessions, int graceMinutes) {
        if (payment.getStatus() != PaymentStatus.PENDING || payment.getDueAt() == null
                || pendingSessions.stream().noneMatch(session -> session != null
                        && session.paymentId().equals(payment.getId()) && session.createdAt().isBefore(payment.getDueAt()))) {
            return null;
        }
        return DepositPaymentWindowPolicy.confirmationUntil(payment.getDueAt(), graceMinutes);
    }

    // Khoản thu đã được ghi nhận bằng đúng giao dịch này (cổng gửi lại kết quả thì vẫn là thành công)
    public static boolean isAppliedBy(Payment payment, BigDecimal amount, Instant paidAt) {
        return payment.getStatus() == PaymentStatus.PAID && paidAt != null && paidAt.equals(payment.getPaidAt())
                && payment.getAmount().compareTo(amount) == 0;
    }

    public static boolean isBalanceOverdue(List<Payment> entries, Instant now) {
        return entries.stream().anyMatch(row -> row.getPaymentType() == PaymentType.BALANCE
                && row.getStatus() == PaymentStatus.PENDING && row.getDueAt() != null && !now.isBefore(row.getDueAt()));
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
            case BALANCE -> (booking.getStatus() == BookingStatus.CONFIRMED
                    || booking.getStatus() == BookingStatus.SCHEDULE_CHANGE_REQUIRED || booking.getStatus() == BookingStatus.IN_OPERATION)
                    && payment.getQuotation().getStatus() == QuotationStatus.ACCEPTED;
            case EXTRA_CHARGE -> acceptsExistingExtraChargePayment(booking.getStatus(), payment)
                    && payment.getQuotation() != null && payment.getQuotation().getStatus() == QuotationStatus.ACCEPTED;
            case REFUND -> false;
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
            case EXTRA_CHARGE -> acceptsExistingExtraChargePayment(booking.getStatus(), payment)
                    && amount.compareTo(payment.getAmount()) == 0;
            case REFUND -> false;
        };
    }

    public static boolean acceptsExtraChargePayment(BookingStatus status) {
        return status == BookingStatus.IN_OPERATION || status == BookingStatus.DELIVERED || status == BookingStatus.COMPLETED;
    }

    private static boolean acceptsExistingExtraChargePayment(BookingStatus status, Payment payment) {
        return acceptsExtraChargePayment(status) || isBalanceHoldChargeStatus(status)
                && !payment.getExtraChargeCosts().isEmpty()
                && payment.getExtraChargeCosts().stream().allMatch(PaymentPolicy::isBalanceHoldCharge);
    }

    private static boolean isBalanceHoldChargeStatus(BookingStatus status) {
        return status == BookingStatus.CONFIRMED || status == BookingStatus.SCHEDULE_CHANGE_REQUIRED || status == BookingStatus.CANCELLED;
    }

    private static boolean isBalanceHoldCharge(CostItem cost) {
        return CostPayerPolicy.isScheduledStablingFee(cost) && cost.getHorseStay().getBalanceHoldAt() != null;
    }

    public static boolean canSelectExtraChargeCost(BookingStatus status, CostItem cost) {
        return acceptsExtraChargePayment(status) && (status != BookingStatus.IN_OPERATION || CostPayerPolicy.isScheduledStablingFee(cost))
                && CostPayerPolicy.customerSide(cost.getPayerType()) && cost.getApprovedAt() != null && cost.getExtraChargePayment() == null;
    }

    public static void requireExtraChargeAllowed(BookingStatus status, List<CostItem> costs) {
        if (isBalanceHoldChargeStatus(status) && !costs.isEmpty()
                && costs.stream().allMatch(PaymentPolicy::isBalanceHoldCharge)) return;
        if (!acceptsExtraChargePayment(status)
                || (status == BookingStatus.IN_OPERATION && !costs.stream().allMatch(CostPayerPolicy::isScheduledStablingFee))) {
            throw BookingRuleException.conflict("Chỉ lập khoản thu thêm khi đã giao, đã hoàn thành hoặc thu phí giữ ngựa khi đang vận chuyển.");
        }
    }

    public static BigDecimal extraChargeAmount(Long bookingId, String currency, int expectedCount, List<CostItem> costs) {
        if (costs.size() != expectedCount || costs.stream().anyMatch(cost -> cost.getBooking() == null
                || !bookingId.equals(cost.getBooking().getId()) || cost.getApprovedAt() == null
                || !CostPayerPolicy.customerSide(cost.getPayerType()) || cost.getExtraChargePayment() != null
                || !currency.equals(cost.getCurrencyCode()) || cost.getAmount().signum() <= 0)) {
            throw BookingRuleException.conflict("Chỉ thu chi phí của đơn, đã duyệt, do khách hoặc người nhận chịu và chưa gắn khoản thu.");
        }
        return costs.stream().map(cost -> cost.getAmount().subtract(cost.getStablingDepositCredit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record RefundAllocation(Payment original, BigDecimal amount) {}

    public static boolean isCommittedRefund(Payment payment) {
        return payment.getPaymentType() == PaymentType.REFUND
                && (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.PAID);
    }

    public static BigDecimal committedRefunds(List<Payment> payments) {
        return payments.stream().filter(PaymentPolicy::isCommittedRefund).map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal paidDeposit(List<Payment> payments) {
        return payments.stream().filter(payment -> payment.getPaymentType() == PaymentType.DEPOSIT
                        && payment.getStatus() == PaymentStatus.PAID)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static BigDecimal unallocatedOverpayment(Quotation quotation, List<Payment> payments) {
        BigDecimal pending = payments.stream()
                .filter(payment -> payment.getPaymentType() == PaymentType.REFUND && payment.getStatus() == PaymentStatus.PENDING)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return outstanding(quotation, payments).negate().subtract(pending).max(BigDecimal.ZERO);
    }

    public static List<RefundAllocation> allocate(List<Payment> payments, String currency, BigDecimal requested) {
        if (requested.signum() < 0) {
            throw BookingRuleException.conflict("Số tiền cần lập hoàn không hợp lệ.");
        }
        BigDecimal remaining = requested;
        List<RefundAllocation> allocations = new ArrayList<>();
        for (Payment original : payments) {
            if (remaining.signum() == 0) {
                break;
            }
            if (original.getStatus() != PaymentStatus.PAID || original.getPaymentType() == PaymentType.REFUND) {
                continue;
            }
            if (!currency.equals(original.getCurrencyCode())) {
                throw BookingRuleException.conflict("Khoản hoàn và khoản gốc phải cùng tiền tệ.");
            }
            BigDecimal alreadyAllocated = payments.stream().filter(PaymentPolicy::isCommittedRefund)
                    .filter(refund -> Objects.equals(refund.getRefundedPayment().getId(), original.getId()))
                    .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal available = original.getAmount().subtract(alreadyAllocated);
            if (available.signum() > 0) {
                BigDecimal amount = available.min(remaining);
                allocations.add(new RefundAllocation(original, amount));
                remaining = remaining.subtract(amount);
            }
        }
        if (remaining.signum() > 0) {
            throw BookingRuleException.conflict("Khoản hoàn vượt số tiền đã trả còn có thể hoàn.");
        }
        return List.copyOf(allocations);
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
