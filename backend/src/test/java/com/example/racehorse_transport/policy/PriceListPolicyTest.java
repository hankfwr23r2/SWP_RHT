package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.*;

import com.example.racehorse_transport.entity.PriceList;
import com.example.racehorse_transport.entity.enums.PriceListStatus;
import com.example.racehorse_transport.exception.PricingRuleException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PriceListPolicyTest {
    @Test
    void validatesDatesRangesAndRates() {
        LocalDate date = LocalDate.of(2026, 11, 23);
        PriceListPolicy.validateDates(date, date);
        PriceListPolicy.validateItem(BigDecimal.TEN, null, BigDecimal.ONE);
        PriceListPolicy.validateRates(BigDecimal.ONE, BigDecimal.ZERO);
        assertThatThrownBy(() -> PriceListPolicy.validateDates(date, date.minusDays(1)))
                .isInstanceOf(PricingRuleException.class);
        assertThatThrownBy(() -> PriceListPolicy.validateItem(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE))
                .isInstanceOf(PricingRuleException.class);
        assertThatThrownBy(() -> PriceListPolicy.validateRates(new BigDecimal("1.01")))
                .isInstanceOf(PricingRuleException.class);
    }

    @Test
    void endsPreviousListTheDayBeforeReplacementAndRejectsSameDay() {
        PriceList old = new PriceList();
        PriceList replacement = new PriceList();
        old.setStatus(PriceListStatus.ACTIVE);
        old.setValidFrom(LocalDate.of(2026, 11, 22));
        replacement.setValidFrom(old.getValidFrom().plusDays(1));
        assertThat(PriceListPolicy.endBeforeReplacement(old, replacement)).isEqualTo(old.getValidFrom());
        PriceListPolicy.requireArchivable(old);
        assertThatThrownBy(() -> PriceListPolicy.requireDraft(old)).isInstanceOf(PricingRuleException.class);
        replacement.setValidFrom(old.getValidFrom());
        assertThatThrownBy(() -> PriceListPolicy.endBeforeReplacement(old, replacement))
                .isInstanceOfSatisfying(PricingRuleException.class, ex -> assertThat(ex.isConflict()).isTrue());
    }
}
