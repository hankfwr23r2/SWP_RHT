package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.ScheduleChangeCause;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.util.HashSet;
import java.util.List;
import java.math.BigDecimal;
import java.util.Set;

// Quy tắc hủy đơn và tách tiền (người dùng chốt 23/11/2026, theo nghiệp vụ cũ trên main).
public final class BookingCancellationPolicy {

    private static final Set<BookingStatus> BEFORE_CONFIRMED = Set.of(BookingStatus.PENDING_INTAKE, BookingStatus.UNDER_REVIEW,
            BookingStatus.NEEDS_RESUBMISSION, BookingStatus.QUOTING, BookingStatus.AWAITING_DEPOSIT, BookingStatus.QUOTE_EXPIRED);

    private BookingCancellationPolicy() {
    }

    public enum Outcome {
        // Chưa trả tiền, hoặc công ty hủy chuyến: không mất phí
        FREE,
        // Đã cọc: mất phần cọc vận chuyển, phần thu hộ do quản lý quyết định hoàn
        FORFEIT_DEPOSIT,
        // Khách không tự hủy được, phải liên hệ quản lý
        NOT_ALLOWED
    }

    public record ScheduleCancellation(Outcome outcome, boolean refundTransportDeposit) {}

    public static ScheduleCancellation scheduleCancellation(ScheduleChangeCause cause, long misses, int refundThreshold) {
        if (cause == null) {
            return new ScheduleCancellation(Outcome.NOT_ALLOWED, false);
        }
        return switch (cause) {
            case COMPANY -> new ScheduleCancellation(Outcome.FREE, false);
            case CUSTOMER -> new ScheduleCancellation(Outcome.FORFEIT_DEPOSIT, false);
            case AUTHORITY -> new ScheduleCancellation(Outcome.FORFEIT_DEPOSIT, misses >= refundThreshold);
        };
    }

    // refundable: hoàn chắc chắn. reviewable: phần thu hộ chờ quản lý quyết định. forfeited: khách mất.
    public record Breakdown(BigDecimal refundable, BigDecimal reviewable, BigDecimal forfeited) {
    }

    public static Outcome byCustomer(BookingStatus status, boolean depositPaid, boolean paidInFull) {
        if (status == BookingStatus.SCHEDULE_CHANGE_REQUIRED) {
            return depositPaid ? Outcome.NOT_ALLOWED : Outcome.FREE;
        }
        if (BEFORE_CONFIRMED.contains(status)) {
            return depositPaid ? Outcome.NOT_ALLOWED : Outcome.FREE;
        }
        if (status == BookingStatus.CONFIRMED) {
            return paidInFull ? Outcome.NOT_ALLOWED : Outcome.FORFEIT_DEPOSIT;
        }
        return Outcome.NOT_ALLOWED;
    }

    public static Breakdown amounts(Outcome outcome, boolean refundTransportDeposit, Quotation quotation,
                                    List<QuotationLine> lines, List<Payment> payments) {
        var paid = payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.PAID && payment.getPaymentType() != PaymentType.REFUND)
                .toList();
        if (paid.isEmpty()) {
            return refundAll(BigDecimal.ZERO);
        }
        if (quotation == null || paid.stream().anyMatch(payment -> !quotation.getCurrencyCode().equals(payment.getCurrencyCode()))) {
            throw BookingRuleException.conflict("Không đối chiếu được khoản đã trả với báo giá.");
        }
        BigDecimal gross = paid.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = gross.subtract(PaymentPolicy.committedRefunds(payments));
        if (net.signum() < 0) {
            throw BookingRuleException.conflict("Khoản hoàn vượt số tiền đã trả.");
        }
        if (outcome == Outcome.FREE) {
            return refundAll(net);
        }
        BigDecimal reviewableTotal = lines.stream().filter(BookingCancellationPolicy::isReviewable)
                .map(QuotationLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deposit = quotation.getDepositAmount().min(gross);
        BigDecimal transport = refundTransportDeposit ? BigDecimal.ZERO : forfeit(deposit, reviewableTotal).forfeited().min(net);
        BigDecimal reviewable = lines.stream().filter(BookingCancellationPolicy::canRefundLine)
                .map(QuotationLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add).min(net.subtract(transport));
        BigDecimal refundable = net.subtract(transport).subtract(reviewable);
        return new Breakdown(refundable, reviewable, transport);
    }

    public static boolean isReviewable(QuotationLine line) {
        return line.getAmount().signum() > 0 && (Boolean.TRUE.equals(line.getPassThrough())
                || line.getBillableService() != null && "VET_CHECK".equals(line.getBillableService().getCode()));
    }

    public static boolean canRefundLine(QuotationLine line) {
        return line.getRefundPayment() == null && isReviewable(line);
    }

    public static Breakdown includeUnspentLines(Breakdown breakdown, List<QuotationLine> lines, List<Long> selected) {
        Set<Long> remaining = new HashSet<>(selected);
        if (remaining.size() != selected.size()) {
            throw BookingRuleException.conflict("Dòng hoàn bị chọn trùng.");
        }
        BigDecimal approved = lines.stream().filter(BookingCancellationPolicy::canRefundLine)
                .filter(line -> remaining.remove(line.getId())).map(QuotationLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!remaining.isEmpty() || approved.compareTo(breakdown.reviewable()) > 0) {
            throw BookingRuleException.conflict("Dòng đã chọn không thuộc phần chờ đối soát của báo giá.");
        }
        return new Breakdown(breakdown.refundable().add(approved), breakdown.reviewable().subtract(approved), breakdown.forfeited());
    }

    // Công ty chỉ hủy được khi chưa nhận ngựa
    public static boolean companyCanCancel(BookingStatus status) {
        return BEFORE_CONFIRMED.contains(status) || status == BookingStatus.CONFIRMED
                || status == BookingStatus.SCHEDULE_CHANGE_REQUIRED;
    }

    // Công ty hủy, hoặc khách hủy không mất phí: hoàn toàn bộ số đã trả, gồm phần thu hộ
    public static Breakdown refundAll(BigDecimal paid) {
        return new Breakdown(paid, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    // Khách hủy sau khi cọc. Tiền cọc gồm toàn bộ phần thu hộ nên phần còn lại là cọc vận chuyển
    public static Breakdown forfeit(BigDecimal paid, BigDecimal passThroughAmount) {
        BigDecimal reviewable = passThroughAmount.min(paid);
        return new Breakdown(BigDecimal.ZERO, reviewable, paid.subtract(reviewable));
    }
}
