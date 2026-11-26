package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.enums.HorseLegType;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.QuotationLineType;
import java.math.BigDecimal;
import java.util.List;

public final class UnclaimedSettlementPolicy {
    private UnclaimedSettlementPolicy() {}

    public static BigDecimal unusedPaidDelivery(Quotation quote, List<QuotationLine> lines, List<Payment> entries,
                                               List<HorseLeg> previousLegs) {
        var unused = lines.stream().filter(line -> line.getLineType() == QuotationLineType.DELIVERY)
                .filter(line -> deliveryDidNotStart(line, previousLegs))
                .map(QuotationLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var pendingRefunds = entries.stream()
                .filter(entry -> entry.getPaymentType() == PaymentType.REFUND && entry.getStatus() == PaymentStatus.PENDING)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var outstanding = PaymentPolicy.outstanding(quote, entries).add(pendingRefunds);
        var available = quote.getTotalAmount().subtract(outstanding).max(BigDecimal.ZERO);
        return unused.subtract(outstanding.max(BigDecimal.ZERO)).max(BigDecimal.ZERO).min(available);
    }

    private static boolean deliveryDidNotStart(QuotationLine line, List<HorseLeg> previousLegs) {
        if (line.getHorseLeg() != null) return line.getHorseLeg().getActualStartAt() == null;
        return previousLegs.stream().noneMatch(leg -> leg.getActualStartAt() != null
                && (leg.getLegType() == HorseLegType.DELIVERY || leg.getLegType() == HorseLegType.DIRECT)
                && (line.getBookingHorse() == null || line.getBookingHorse().equals(leg.getBookingHorse())));
    }

    public static BigDecimal credit(BigDecimal unused, BigDecimal fee) {
        return unused.min(fee);
    }
}
