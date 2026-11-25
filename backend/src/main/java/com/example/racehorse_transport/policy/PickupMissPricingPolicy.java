package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.HorseInsurance;
import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import com.example.racehorse_transport.entity.enums.InsuranceCoverageOption;
import com.example.racehorse_transport.exception.BookingRuleException;
import com.example.racehorse_transport.entity.enums.QuotationLineType;
import java.math.BigDecimal;

public final class PickupMissPricingPolicy {
    private PickupMissPricingPolicy() {}

    public static boolean retainExcludedLine(QuotationLine line, boolean customer, boolean completed) {
        return customer && (line.getLineType() == QuotationLineType.INSURANCE
                || line.getLineType() == QuotationLineType.SERVICE && completed);
    }

    public static BigDecimal depositFloorAdjustment(BigDecimal total, BigDecimal deposit) {
        return deposit.subtract(total).max(BigDecimal.ZERO);
    }

    public record Settlement(BigDecimal balanceDue, BigDecimal refund) {}

    public static Settlement settlement(BigDecimal outstanding) {
        return new Settlement(outstanding.max(BigDecimal.ZERO), outstanding.negate().max(BigDecimal.ZERO));
    }

    public static boolean newPremiumRefund(HorseInsurance insurance, BigDecimal cumulative) {
        var previous = insurance.getRefundedPremiumAmount() == null ? BigDecimal.ZERO : insurance.getRefundedPremiumAmount();
        if (insurance.getCoverageOption() != InsuranceCoverageOption.ARRANGED
                || insurance.getStatus() != HorseInsuranceStatus.CONFIRMED || insurance.getPremiumAmount() == null
                || cumulative.signum() <= 0 || cumulative.compareTo(insurance.getPremiumAmount()) > 0
                || cumulative.compareTo(previous) < 0) {
            throw BookingRuleException.conflict("Số phí bảo hiểm đã hoàn không khớp với hợp đồng.");
        }
        return cumulative.compareTo(previous) > 0;
    }

    public static BigDecimal remainingPremium(HorseInsurance insurance, BigDecimal quotedUnitPrice) {
        return insurance.getRefundedPremiumAmount() == null ? quotedUnitPrice
                : quotedUnitPrice.min(insurance.getPremiumAmount().subtract(insurance.getRefundedPremiumAmount()));
    }
}
