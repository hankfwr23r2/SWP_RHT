package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.PriceList;
import com.example.racehorse_transport.entity.enums.PriceListStatus;
import com.example.racehorse_transport.exception.PricingRuleException;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class PriceListPolicy {
    private PriceListPolicy() {}

    public static void validateDates(LocalDate from, LocalDate to) {
        if (to != null && to.isBefore(from)) {
            throw new PricingRuleException("Ngày kết thúc phải từ ngày bắt đầu trở đi.");
        }
    }

    public static void validateRates(BigDecimal... rates) {
        for (BigDecimal rate : rates) {
            if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0 || rate.scale() > 4) {
                throw new PricingRuleException("Tỷ lệ phải từ 0 đến 1 và có tối đa 4 chữ số thập phân.");
            }
        }
    }

    public static void validateItem(BigDecimal from, BigDecimal to, BigDecimal unitPrice) {
        if (from == null && to != null || from != null && from.signum() < 0
                || to != null && from.compareTo(to) >= 0) {
            throw new PricingRuleException("Khoảng km không hợp lệ.");
        }
        if (unitPrice == null || unitPrice.signum() < 0 || unitPrice.scale() > 2
                || unitPrice.precision() - unitPrice.scale() > 13) {
            throw new PricingRuleException("Đơn giá không hợp lệ.");
        }
    }

    public static void requireDraft(PriceList list) {
        requireStatus(list, PriceListStatus.DRAFT);
    }

    public static void requireArchivable(PriceList list) {
        requireStatus(list, PriceListStatus.ACTIVE);
    }

    public static LocalDate endBeforeReplacement(PriceList previous, PriceList replacement) {
        if (!previous.getValidFrom().isBefore(replacement.getValidFrom())) {
            throw new PricingRuleException("Không thể kết thúc bảng giá hiện hành trước bảng giá mới.", true);
        }
        return replacement.getValidFrom().minusDays(1);
    }

    private static void requireStatus(PriceList list, PriceListStatus status) {
        if (list.getStatus() != status) {
            throw new PricingRuleException("Trạng thái bảng giá không cho phép thao tác này.", true);
        }
    }
}
