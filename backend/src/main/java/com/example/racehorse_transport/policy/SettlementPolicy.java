package com.example.racehorse_transport.policy;

import java.math.BigDecimal;

// Số còn nợ của đơn (DB_SCHEMA_59_BANG.md mục 5). Mọi số tiền cùng tiền tệ với báo giá (quy tắc #22).
public final class SettlementPolicy {

    private SettlementPolicy() {
    }

    // customerCosts: chi phí đã duyệt do khách hoặc người nhận chịu. Kết quả âm nghĩa là khách đã trả dư.
    public static BigDecimal outstanding(BigDecimal acceptedTotal, BigDecimal customerCosts, BigDecimal paid, BigDecimal refunded) {
        return acceptedTotal.add(customerCosts).subtract(paid).add(refunded);
    }

    public static boolean isPaidInFull(BigDecimal outstanding) {
        return outstanding.signum() <= 0;
    }
}
