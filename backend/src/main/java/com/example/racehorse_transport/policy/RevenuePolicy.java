package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.exception.DispatchRuleException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

// Báo cáo doanh thu, hàm thuần. Tiền thu hộ (bảo hiểm, phí thông quan...) không phải doanh thu công ty
public final class RevenuePolicy {

    public static final Duration MAX_RANGE = Duration.ofDays(366);

    private RevenuePolicy() {}

    public static void requireRange(Instant from, Instant to) {
        if (!from.isBefore(to)) throw new DispatchRuleException("to", "Ngày kết thúc phải sau ngày bắt đầu.");
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new DispatchRuleException("to", "Báo cáo doanh thu tối đa 366 ngày.");
        }
    }

    // Đơn hủy: chỉ phần công ty giữ lại sau hoàn tiền và sau tiền thu hộ. Đơn khác: báo giá đã chấp nhận trừ thu hộ, cộng phụ thu đã trả
    public static BigDecimal bookingRevenue(boolean cancelled, BigDecimal quotedTotal, BigDecimal passThrough,
                                            BigDecimal extraPaid, BigDecimal received, BigDecimal refunded) {
        if (cancelled) return received.subtract(refunded).subtract(passThrough).max(BigDecimal.ZERO);
        return quotedTotal.subtract(passThrough).add(extraPaid);
    }
}
