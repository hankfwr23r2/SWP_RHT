package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.example.racehorse_transport.entity.enums.InsuranceCoverageOption.*;
import com.example.racehorse_transport.entity.HorseInsurance;
import com.example.racehorse_transport.entity.TermsAcceptance;
import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InsuranceReviewPolicyTest {
    private static final Instant NOW = Instant.parse("2026-11-23T00:00:00Z");
    private static final BigDecimal PREMIUM = new BigDecimal("2500");

    @Test
    void arrangedRequiresAnInsurerPositiveWholePremiumAndAnUnexpiredQuote() {
        assertThatCode(() -> InsuranceReviewPolicy.validateEvidence(ARRANGED, "Hãng A", PREMIUM,
                NOW.plusSeconds(1), NOW)).doesNotThrowAnyException();
        assertThatThrownBy(() -> InsuranceReviewPolicy.validateEvidence(ARRANGED, "Hãng A", new BigDecimal("2.5"),
                NOW.plusSeconds(1), NOW)).isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> InsuranceReviewPolicy.validateEvidence(ARRANGED, "Hãng A", PREMIUM,
                NOW, NOW)).isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> InsuranceReviewPolicy.validateEvidence(PERSONAL, "Hãng A", PREMIUM,
                null, NOW)).isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> InsuranceReviewPolicy.validateEvidence(ARRANGED, " ", PREMIUM,
                NOW.plusSeconds(1), NOW)).isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> InsuranceReviewPolicy.validateEvidence(ARRANGED, "A".repeat(201), PREMIUM,
                NOW.plusSeconds(1), NOW)).isInstanceOf(BookingRuleException.class);
    }

    @Test
    void coveragePeriodIncludesPickupAndDeparture() {
        LocalDate pickup = LocalDate.of(2026, 11, 23);
        LocalDate departure = pickup.plusDays(1);
        assertThatCode(() -> InsuranceReviewPolicy.validatePeriod(pickup, departure, pickup, departure))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> InsuranceReviewPolicy.validatePeriod(pickup, pickup, pickup, departure))
                .isInstanceOf(BookingRuleException.class);
    }

    @Test
    void personalInsuranceNeedsOnlyAFileAndAValidPeriod() {
        LocalDate pickup = LocalDate.of(2026, 11, 23);
        HorseInsurance insurance = new HorseInsurance();
        insurance.setCoverageOption(PERSONAL);
        insurance.setStatus(HorseInsuranceStatus.DRAFT);
        insurance.setValidFrom(pickup);
        insurance.setValidTo(pickup.plusDays(1));

        assertThatThrownBy(() -> InsuranceReviewPolicy.validateConfirmation(insurance, pickup, pickup.plusDays(1), NOW, false))
                .isInstanceOfSatisfying(BookingRuleException.class, ex -> {
                    org.assertj.core.api.Assertions.assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    org.assertj.core.api.Assertions.assertThat(ex.getReason()).contains("tệp hợp đồng");
                });
        assertThatCode(() -> InsuranceReviewPolicy.validateConfirmation(insurance, pickup, pickup.plusDays(1), NOW, true))
                .doesNotThrowAnyException();
    }

    @Test
    void noneRequiresWaiverAndCannotBeConfirmedTwice() {
        HorseInsurance insurance = new HorseInsurance();
        insurance.setStatus(HorseInsuranceStatus.DRAFT);
        insurance.setCoverageOption(NONE);
        assertThatThrownBy(() -> InsuranceReviewPolicy.validateConfirmation(insurance, null, null, NOW, false))
                .isInstanceOf(BookingRuleException.class);
        insurance.setWaiverTermsAcceptance(new TermsAcceptance());
        assertThatCode(() -> InsuranceReviewPolicy.validateConfirmation(insurance, null, null, NOW, false))
                .doesNotThrowAnyException();
        insurance.setStatus(HorseInsuranceStatus.CONFIRMED);
        assertThatThrownBy(() -> InsuranceReviewPolicy.validateConfirmation(insurance, null, null, NOW, false))
                .isInstanceOfSatisfying(BookingRuleException.class, ex ->
                        org.assertj.core.api.Assertions.assertThat(ex.getStatusCode().value()).isEqualTo(409));
    }
}
