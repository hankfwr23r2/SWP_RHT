package com.example.racehorse_transport.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Tiền cọc gồm toàn bộ phần thu hộ; tỷ lệ chỉ áp lên cước dịch vụ của công ty (DB_SCHEMA_59_BANG.md mục 5).
public final class DepositPolicy {

    private static final BigDecimal ROUNDING_UNIT = BigDecimal.valueOf(1000);

    private DepositPolicy() {
    }

    public static BigDecimal deposit(BigDecimal depositRate, BigDecimal totalAmount, BigDecimal passThroughAmount) {
        if (depositRate.signum() < 0 || depositRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Tỷ lệ cọc phải từ 0 đến 1.");
        }
        if (passThroughAmount.signum() < 0 || passThroughAmount.compareTo(totalAmount) > 0) {
            throw new IllegalArgumentException("Phần thu hộ phải từ 0 đến tổng báo giá.");
        }
        // Làm tròn lên nghìn đồng; tổng báo giá không đổi nên số dư = tổng - cọc. Không vượt tổng (CHECK của quotation)
        return depositRate.multiply(totalAmount.subtract(passThroughAmount)).add(passThroughAmount)
                .divide(ROUNDING_UNIT, 0, RoundingMode.CEILING).multiply(ROUNDING_UNIT)
                .min(totalAmount).setScale(2, RoundingMode.UNNECESSARY);
    }
}
