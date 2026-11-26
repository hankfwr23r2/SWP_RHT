package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.CostItem;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

// Số còn nợ của đơn (DB_SCHEMA_59_BANG.md mục 5). Mọi số tiền cùng tiền tệ với báo giá (quy tắc #22).
public final class SettlementPolicy {

    private SettlementPolicy() {
    }

    // customerCosts: chi phí đã duyệt do khách hoặc người nhận chịu. Kết quả âm nghĩa là khách đã trả dư.
    public static BigDecimal outstanding(BigDecimal acceptedTotal, BigDecimal customerCosts, BigDecimal paid, BigDecimal refunded) {
        return acceptedTotal.add(customerCosts).subtract(paid).add(refunded);
    }

    // Chi phí phía khách đã có kết quả: bị từ chối, hoặc đã duyệt và đã thành khoản thu (mục 13.3 điều 3)
    public static boolean isSettled(CostItem cost) {
        if (!CostPayerPolicy.customerSide(cost.getPayerType())) return true;
        if (cost.getApprovedAt() == null) return Boolean.FALSE.equals(cost.getCustomerAgreed());
        return cost.getExtraChargePayment() != null || cost.getAmount().compareTo(cost.getStablingDepositCredit()) <= 0;
    }

    // Đóng đơn không phụ thuộc khoản thu thêm đã trả hay chưa; công nợ theo dõi riêng (mục 13.3)
    public static boolean canClose(BigDecimal quotationOutstanding, boolean openIncident, List<CostItem> costs,
                                   boolean pendingRefund) {
        return quotationOutstanding.signum() == 0 && !openIncident && !pendingRefund
                && costs.stream().allMatch(SettlementPolicy::isSettled);
    }

    public static boolean elapsed(Instant since, Instant now, int days) {
        return since != null && !now.isBefore(since.plus(Duration.ofDays(days)));
    }

    public static boolean isPaidInFull(BigDecimal outstanding) {
        return outstanding.signum() <= 0;
    }
}
