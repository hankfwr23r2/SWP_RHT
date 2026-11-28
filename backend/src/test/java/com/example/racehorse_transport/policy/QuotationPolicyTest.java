package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.enums.QuotationLineType;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuotationPolicyTest {
    @Test
    void deadlinesUseTheEarliestOfConfiguredHoursLocalMidnightAndInsurance() {
        Instant now = Instant.parse("2027-01-15T10:00:00Z");
        var pickup = LocalDate.of(2027, 1, 15);
        assertThat(QuotationPolicy.deadline(now, 24, pickup, List.of())).isEqualTo("2027-01-15T17:00:00Z");
        assertThat(QuotationPolicy.deadline(now, 2, pickup, List.of())).isEqualTo("2027-01-15T12:00:00Z");
        assertThat(QuotationPolicy.deadline(now, 24, pickup, List.of(now.plusSeconds(60)))).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void rejectsDiscountAgainstPassThroughAndCapsReplacementDiscount() {
        var insurance = line(QuotationLineType.INSURANCE, "2300", true);
        var transport = line(QuotationLineType.LINEHAUL, "4500", false);
        var discount = line(QuotationLineType.ADJUSTMENT, "-5000", false);
        var rows = List.of(insurance, transport, discount);
        assertThatThrownBy(() -> QuotationPolicy.totals(rows, new BigDecimal("0.30")))
                .isInstanceOf(BookingRuleException.class);
        var capped = QuotationPolicy.cappedDiscounts(rows);
        assertThat(discount.getUnitPrice()).isEqualByComparingTo("-5000");
        capped.forEach(QuotationLine::setUnitPrice);
        assertThat(discount.getUnitPrice()).isEqualByComparingTo("-4500");
        var totals = QuotationPolicy.totals(rows, new BigDecimal("0.30"));
        assertThat(totals.total()).isEqualByComparingTo("2300");
        assertThat(totals.passThrough()).isEqualByComparingTo("2300");
        assertThat(totals.deposit()).isEqualByComparingTo("2300");
    }

    @Test
    void issuedQuotationCannotBecomeDraft() {
        assertThatThrownBy(() -> QuotationPolicy.requireTransition(QuotationStatus.SENT, QuotationStatus.DRAFT))
                .isInstanceOf(BookingRuleException.class);
    }

    private static QuotationLine line(QuotationLineType type, String price, boolean passThrough) {
        var line = new QuotationLine();
        line.setLineType(type);
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal(price));
        line.setPassThrough(passThrough);
        return line;
    }
}
