package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

// Công nợ khoản thu thêm (PHUONG_AN_6_LUONG mục 13.3, 13.5 điểm 6, 8, 9), hàm thuần
public final class CustomerDebtPolicy {
    private CustomerDebtPolicy() {}

    public static boolean isOpen(Payment payment) {
        return payment.getPaymentType() == PaymentType.EXTRA_CHARGE && payment.getStatus() == PaymentStatus.PENDING;
    }

    public static boolean isDisputed(Payment payment) {
        return payment.getDisputedAt() != null && payment.getDisputeResolvedAt() == null;
    }

    public static boolean isOverdue(Payment payment, Instant now) {
        return isOpen(payment) && !isDisputed(payment) && payment.getDueAt() != null && payment.getDueAt().isBefore(now);
    }

    public static boolean isBad(Payment payment, Instant now, int badDays) {
        return isOverdue(payment, now) && payment.getIssuedAt() != null
                && !now.isBefore(payment.getIssuedAt().plus(Duration.ofDays(badDays)));
    }

    // Nợ quá hạn đủ mức, hoặc khoản đã xóa nợ mà quản lý chưa mở lại quyền đặt đơn
    public static boolean blocksSubmission(List<Payment> debts, Instant now, BigDecimal minimum) {
        if (debts.stream().anyMatch(row -> row.getWrittenOffAt() != null && row.getBlockReleasedAt() == null)) return true;
        return hasQualifyingOverdue(debts, now, minimum);
    }

    public static boolean hasQualifyingOverdue(List<Payment> debts, Instant now, BigDecimal minimum) {
        var overdue = debts.stream().filter(row -> isOverdue(row, now)).map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return overdue.signum() > 0 && overdue.compareTo(minimum) >= 0;
    }

    public static void requireDisputable(Payment payment) {
        if (!isOpen(payment) || payment.getDisputedAt() != null) {
            throw BookingRuleException.conflict("Khoản này không khiếu nại được: đã trả, đã hủy hoặc đã khiếu nại một lần.");
        }
    }

    public static void requireDisputePending(Payment payment) {
        if (!isOpen(payment) || !isDisputed(payment)) {
            throw BookingRuleException.conflict("Khoản này không có khiếu nại chờ kết luận.");
        }
    }

    // Hạn trả lùi đúng bằng thời gian chờ kết luận
    public static Instant dueAfterDispute(Payment payment, Instant now) {
        return payment.getDueAt() == null ? null : payment.getDueAt().plus(Duration.between(payment.getDisputedAt(), now));
    }

    public static void requireExtendable(Payment payment, int days, int maxDays) {
        if (!isOpen(payment) || payment.getExtendedAt() != null || days < 1 || days > maxDays) {
            throw BookingRuleException.conflict("Chỉ gia hạn một lần cho khoản chưa trả, tối đa " + maxDays + " ngày.");
        }
    }

    public static void requireWriteOff(Payment payment, Instant now, int badDays) {
        if (!isBad(payment, now, badDays)) {
            throw BookingRuleException.conflict("Chỉ xóa nợ cho khoản đã sang nợ khó thu và không đang khiếu nại.");
        }
    }

    public static void requireWrittenOff(Payment payment) {
        if (payment.getWrittenOffAt() == null) {
            throw BookingRuleException.conflict("Khoản này chưa xóa nợ.");
        }
    }
}
