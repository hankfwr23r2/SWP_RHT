package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.dto.booking.InsuranceDeclarationRequestDto;
import com.example.racehorse_transport.entity.PartyRole;
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
                InsuranceCoverageOption.ARRANGED, "ignored", START, START.minusDays(1), -1L, false)))
                .doesNotThrowAnyException();
    }

    @Test
    void roleValidityIncludesBothBoundaryDates() {
        PartyRole role = new PartyRole();
        role.setValidFrom(START);
        role.setValidTo(START.plusDays(1));
        assertThat(InsuranceDeclarationPolicy.isRoleEffective(role, START.minusDays(1))).isFalse();
        assertThat(InsuranceDeclarationPolicy.isRoleEffective(role, START)).isTrue();
        assertThat(InsuranceDeclarationPolicy.isRoleEffective(role, START.plusDays(1))).isTrue();
        assertThat(InsuranceDeclarationPolicy.isRoleEffective(role, START.plusDays(2))).isFalse();
    }
}
