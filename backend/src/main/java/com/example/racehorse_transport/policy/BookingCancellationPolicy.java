package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.ScheduleChangeCause;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.util.List;
import java.math.BigDecimal;
import java.util.Set;

// Quy tắc hủy đơn và tách tiền (người dùng chốt 23/11/2026, theo nghiệp vụ cũ trên main; 24/11/2026: khoản thu hộ không hoàn).
public final class BookingCancellationPolicy {

    private static final Set<BookingStatus> BEFORE_CONFIRMED = Set.of(BookingStatus.PENDING_INTAKE, BookingStatus.UNDER_REVIEW,
            BookingStatus.NEEDS_RESUBMISSION, BookingStatus.QUOTING, BookingStatus.AWAITING_DEPOSIT, BookingStatus.QUOTE_EXPIRED);

    private BookingCancellationPolicy() {
    }

    public enum Outcome {
        // Chưa trả tiền, hoặc công ty hủy chuyến: không mất phí
        FREE,
        // Đã cọc: mất cọc, gồm cả phần thu hộ. Cơ quan chậm đủ số lần thì chỉ mất phần thu hộ
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

    // refundable: hoàn chắc chắn. forfeited: khách mất. reviewable luôn bằng 0, giữ để không đổi hợp đồng API.
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
        BigDecimal nonRefundable = lines.stream().filter(BookingCancellationPolicy::isNonRefundable)
                .map(QuotationLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        // Tiền cọc đã gồm toàn bộ phần thu hộ, nên mất cọc là mất luôn phần thu hộ
        BigDecimal kept = refundTransportDeposit ? nonRefundable : quotation.getDepositAmount().max(nonRefundable);
        BigDecimal forfeited = kept.min(net);
        return new Breakdown(net.subtract(forfeited), BigDecimal.ZERO, forfeited);
    }

    // Khoản thu hộ: khách hủy hoặc hủy do cơ quan thì không hoàn
    private static boolean isNonRefundable(QuotationLine line) {
        return line.getAmount().signum() > 0 && Boolean.TRUE.equals(line.getPassThrough());
    }

    // API vẫn nhận danh sách dòng để giữ hợp đồng, nhưng không dòng nào được chọn hoàn riêng
    public static void requireNoLineRefund(List<Long> selected) {
        if (!selected.isEmpty()) {
            throw BookingRuleException.conflict("Khoản thu hộ không được hoàn riêng khi hủy đơn.");
        }
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
}
