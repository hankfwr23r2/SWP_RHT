package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.dto.booking.InsuranceDeclarationRequestDto;
import com.example.racehorse_transport.entity.enums.InsuranceCoverageOption;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class InsuranceDeclarationPolicyTest {

    private static final LocalDate START = LocalDate.of(2027, 1, 5);

    @Test
    void personalRequiresPolicyNumberAndNonReversedDates() {
        assertThatCode(() -> InsuranceDeclarationPolicy.validate(new InsuranceDeclarationRequestDto(
                InsuranceCoverageOption.PERSONAL, "POL-1", START, START, null, null))).doesNotThrowAnyException();
        assertThatThrownBy(() -> InsuranceDeclarationPolicy.validate(new InsuranceDeclarationRequestDto(
                InsuranceCoverageOption.PERSONAL, "POL-1", START, START.minusDays(1), null, null)))
                .isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> InsuranceDeclarationPolicy.validate(new InsuranceDeclarationRequestDto(
                InsuranceCoverageOption.PERSONAL, " ", START, START, null, null)))
                .isInstanceOf(BookingRuleException.class);
    }

    @Test
    void onlyNoneRequiresWaiverAndArrangedIgnoresOtherValues() {
        assertThatThrownBy(() -> InsuranceDeclarationPolicy.validate(new InsuranceDeclarationRequestDto(
                InsuranceCoverageOption.NONE, null, null, null, null, false))).isInstanceOf(BookingRuleException.class);
        assertThatCode(() -> InsuranceDeclarationPolicy.validate(new InsuranceDeclarationRequestDto(
                InsuranceCoverageOption.NONE, null, null, null, null, true))).doesNotThrowAnyException();
        assertThatCode(() -> InsuranceDeclarationPolicy.validate(new InsuranceDeclarationRequestDto(
                InsuranceCoverageOption.ARRANGED, "ignored", START, START.minusDays(1), "ignored", false)))
                .doesNotThrowAnyException();
    }

}
