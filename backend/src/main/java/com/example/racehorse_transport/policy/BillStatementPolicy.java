package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Predicate;

// Số liệu in trên mail hóa đơn gửi khách, tính từ báo giá đã chấp nhận và các khoản thanh toán của đơn. Hàm thuần.
// Chỉ dùng khoản thu, khoản hoàn của khách; chi phí nội bộ của công ty không bao giờ in ra mail.
public final class BillStatementPolicy {

    public record Statement(BigDecimal quoted, BigDecimal extraCharges, BigDecimal paid, BigDecimal refunded,
                            BigDecimal refundPending, BigDecimal outstanding) {

        // Phần công ty giữ lại khi hủy: đã trả trừ mọi khoản hoàn (đã chuyển hoặc đang chờ chuyển)
        public BigDecimal retained() {
            return paid.subtract(refunded).subtract(refundPending);
        }
    }

    private BillStatementPolicy() {
    }

    public static Statement statement(BigDecimal quotedTotal, List<Payment> payments) {
        BigDecimal quoted = quotedTotal == null ? BigDecimal.ZERO : quotedTotal;
        BigDecimal extra = sum(payments, p -> p.getPaymentType() == PaymentType.EXTRA_CHARGE && p.getStatus() != PaymentStatus.FAILED);
        BigDecimal paid = sum(payments, p -> p.getPaymentType() != PaymentType.REFUND && p.getStatus() == PaymentStatus.PAID);
        BigDecimal refunded = sum(payments, p -> p.getPaymentType() == PaymentType.REFUND && p.getStatus() == PaymentStatus.PAID);
        BigDecimal pending = sum(payments, p -> p.getPaymentType() == PaymentType.REFUND && p.getStatus() == PaymentStatus.PENDING);
        return new Statement(quoted, extra, paid, refunded, pending, SettlementPolicy.outstanding(quoted, extra, paid, refunded));
    }

    // Số dư của báo giá: tổng trừ cọc
    public static BigDecimal balance(BigDecimal total, BigDecimal deposit) {
        return (total == null ? BigDecimal.ZERO : total).subtract(deposit == null ? BigDecimal.ZERO : deposit);
    }

    private static BigDecimal sum(List<Payment> payments, Predicate<Payment> filter) {
        return payments.stream().filter(filter).map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
