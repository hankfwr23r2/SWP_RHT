package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.enums.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnclaimedSettlementPolicyTest {

    @Test
    void onlyUnusedDeliveryThatWasPaidCanBeCredited() {
        var quote = new Quotation();
        quote.setCurrencyCode("VND");
        quote.setTotalAmount(new BigDecimal("1000"));
        var payment = new Payment();
        payment.setPaymentType(PaymentType.BALANCE);
        payment.setStatus(PaymentStatus.PAID);
        payment.setCurrencyCode("VND");
        payment.setAmount(new BigDecimal("900"));
        var started = line(QuotationLineType.DELIVERY, Instant.EPOCH);
        var unused = line(QuotationLineType.DELIVERY, null);
        var pickup = line(QuotationLineType.PICKUP, null);

        var credit = UnclaimedSettlementPolicy.unusedPaidDelivery(quote, List.of(started, unused, pickup), List.of(payment), List.of());
        assertThat(credit).isEqualByComparingTo("200");
        assertThat(UnclaimedSettlementPolicy.credit(credit, new BigDecimal("80"))).isEqualByComparingTo("80");
        assertThat(UnclaimedSettlementPolicy.unusedPaidDelivery(quote, List.of(unused), List.of(), List.of())).isZero();
    }

    private static QuotationLine line(QuotationLineType type, Instant startedAt) {
        var line = new QuotationLine();
        var leg = new HorseLeg();
        leg.setActualStartAt(startedAt);
        line.setHorseLeg(leg);
        line.setLineType(type);
        line.setAmount(new BigDecimal("300"));
        return line;
    }
}
