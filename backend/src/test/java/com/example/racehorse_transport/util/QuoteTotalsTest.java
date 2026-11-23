package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.manager.QuoteAdjustmentDto;
import com.example.racehorse_transport.dto.manager.QuoteLineDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteTotalsTest {

    private static QuoteLineDto line(long amount) {
        QuoteLineDto l = new QuoteLineDto();
        l.setLabel("Cước");
        l.setAmount(BigDecimal.valueOf(amount));
        return l;
    }

    private static QuoteAdjustmentDto adjustment(long amount) {
        QuoteAdjustmentDto a = new QuoteAdjustmentDto();
        a.setLabel("Giảm giá khách quen");
        a.setAmount(BigDecimal.valueOf(amount));
        return a;
    }

    @Test
    void totalsAreComputedFromTheLinesAndAdjustments() {
        QuoteTotals t = QuoteTotals.of(List.of(line(10_000_000), line(2_500_000)), List.of(adjustment(-500_000)));

        assertThat(t.subtotal()).isEqualByComparingTo("12500000");
        assertThat(t.total()).isEqualByComparingTo("12000000");
        assertThat(t.deposit()).isEqualByComparingTo("3600000");
        assertThat(t.balance()).isEqualByComparingTo("8400000");
    }

    @Test
    void depositIsRoundedToTheThousandLikeTheQuoteScreen() {
        QuoteTotals t = QuoteTotals.of(List.of(line(1_234_567)), List.of());

        assertThat(t.deposit()).isEqualByComparingTo("370000"); // 370 370,1 làm tròn tới nghìn
        assertThat(t.balance()).isEqualByComparingTo("864567");
    }

    @Test
    void depositUsesTheConfiguredRate() {
        QuoteTotals totals = QuoteTotals.of(List.of(line(10_000_000)), List.of(), new BigDecimal("0.25"));

        assertThat(totals.deposit()).isEqualByComparingTo("2500000");
        assertThat(totals.balance()).isEqualByComparingTo("7500000");
    }

    @Test
    void discountsCannotTakeTheTotalBelowZero() {
        QuoteTotals t = QuoteTotals.of(List.of(line(1_000_000)), List.of(adjustment(-2_000_000)));

        assertThat(t.total()).isEqualByComparingTo("0");
        assertThat(t.deposit()).isEqualByComparingTo("0");
    }

    @Test
    void aQuoteNeedsLinesWithNonNegativeAmounts() {
        assertThatThrownBy(() -> QuoteTotals.of(List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QuoteTotals.of(List.of(line(-1)), List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
