package com.example.racehorse_transport.util;
import com.example.racehorse_transport.dto.booking.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class HorseInsurancePolicyTest {
    private BookingHorseDto horse(String id, HorseInsuranceDto i) {
        return BookingHorseDto.builder().horseId(id).name(id).insurance(i).build();
    }
    @Test void rejectsMissingOrInvalidDeclarationsAndPersonalDates() {
        assertThatThrownBy(() -> HorseInsurancePolicy.validateDeclaration(null)).isInstanceOf(IllegalArgumentException.class);
        var personal = HorseInsuranceDto.builder().option("personal").provider("Provider").policyNumber("P1").validFrom("2026-12-02").validUntil("2026-12-01").build();
        assertThatThrownBy(() -> HorseInsurancePolicy.validateDeclaration(personal)).hasMessageContaining("Thời hạn");
    }
    @Test void mixedOrderOnlyCollectsConfirmedAssistancePremium() {
        var supported = HorseInsuranceDto.builder().option("assistance").provider("Provider").premium(new BigDecimal("1250000")).reviewed(true).build();
        var personal = HorseInsuranceDto.builder().option("personal").provider("Personal").policyNumber("P1").validFrom("2026-01-01").validUntil("2027-01-01").reviewed(true).build();
        var horses = List.of(horse("H1", supported), horse("H2", personal), horse("H3", HorseInsuranceDto.builder().option("none").build()));
        HorseInsurancePolicy.requireQuoteReady(horses);
        assertThat(HorseInsurancePolicy.quoteLines(horses)).hasSize(1);
        assertThat(HorseInsurancePolicy.quoteLines(horses).getFirst().getAmount()).isEqualByComparingTo("1250000");
        supported.setReviewed(false);
        assertThatThrownBy(() -> HorseInsurancePolicy.requireQuoteReady(horses)).isInstanceOf(IllegalStateException.class);
    }
    @Test void premiumIsNotProofOfEffectivePolicyAndExcludedHorseDoesNotBlockTrip() {
        var insurance = HorseInsuranceDto.builder().option("assistance").provider("Provider").premium(BigDecimal.TEN).reviewed(true).build();
        var horses = List.of(horse("H1", insurance));
        var departure = Instant.parse("2026-12-01T00:00:00Z");
        assertThatThrownBy(() -> HorseInsurancePolicy.requireDepartureReady(horses, List.of("H1"), departure)).isInstanceOf(IllegalStateException.class);
        HorseInsurancePolicy.requireDepartureReady(horses, List.of("H2"), departure);
        insurance.setArranged(true); insurance.setPolicyNumber("P1"); insurance.setValidFrom("2026-11-01"); insurance.setValidUntil("2026-11-30");
        assertThatThrownBy(() -> HorseInsurancePolicy.requireDepartureReady(horses, List.of("H1"), departure)).hasMessageContaining("khởi hành");
        insurance.setValidUntil("2026-12-02");
        HorseInsurancePolicy.requireDepartureReady(horses, List.of("H1"), departure);
    }
    @Test void cancellationAllocatesOnlyCollectedPremiumAndPreservesLegacyDeposit() {
        var quote = Map.of("total", 10000000, "lines", List.of(Map.of("label", HorseInsurancePolicy.LABEL, "amount", 2000000), Map.of("label", "Vận chuyển", "amount", 8000000)));
        assertThat(HorseInsurancePolicy.insuranceDeposit(quote, new BigDecimal("3000000"))).isEqualByComparingTo("600000");
        assertThat(HorseInsurancePolicy.insuranceDeposit(Map.of("total", 1000, "lines", List.of()), new BigDecimal("300"))).isZero();
    }
}
