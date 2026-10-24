package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.manager.QuoteAdjustmentDto;
import com.example.racehorse_transport.dto.manager.QuoteLineDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// Tổng tiền của báo giá do backend tự tính từ các dòng chi phí và khoản điều chỉnh, không nhận số tổng từ client.
// Cùng công thức với màn duyệt báo giá: tổng không âm, cọc theo tỷ lệ cấu hình và làm tròn tới nghìn đồng.
public record QuoteTotals(BigDecimal subtotal, BigDecimal adjustments, BigDecimal total, BigDecimal deposit, BigDecimal balance) {

    public static final BigDecimal DEPOSIT_RATE = new BigDecimal("0.30");
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    public static QuoteTotals of(List<QuoteLineDto> lines, List<QuoteAdjustmentDto> adjustments) {
        return of(lines, adjustments, DEPOSIT_RATE);
    }

    public static QuoteTotals of(
            List<QuoteLineDto> lines,
            List<QuoteAdjustmentDto> adjustments,
            BigDecimal depositRate) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Báo giá phải có ít nhất một dòng chi phí.");
        }
        if (depositRate == null || depositRate.signum() <= 0 || depositRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Tỷ lệ đặt cọc phải lớn hơn 0 và nhỏ hơn 1.");
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        for (QuoteLineDto line : lines) {
            if (line.getAmount() == null || line.getAmount().signum() < 0) {
                throw new IllegalArgumentException("Số tiền của dòng \"" + line.getLabel() + "\" không hợp lệ.");
            }
            subtotal = subtotal.add(line.getAmount());
        }
        BigDecimal adjustmentSum = BigDecimal.ZERO;
        if (adjustments != null) {
            for (QuoteAdjustmentDto adjustment : adjustments) {
                if (adjustment.getAmount() != null) adjustmentSum = adjustmentSum.add(adjustment.getAmount());
            }
        }
        BigDecimal total = subtotal.add(adjustmentSum).max(BigDecimal.ZERO);
        BigDecimal deposit = total.multiply(depositRate).divide(THOUSAND, 0, RoundingMode.HALF_UP).multiply(THOUSAND);
        return new QuoteTotals(subtotal, adjustmentSum, total, deposit, total.subtract(deposit));
    }
}
