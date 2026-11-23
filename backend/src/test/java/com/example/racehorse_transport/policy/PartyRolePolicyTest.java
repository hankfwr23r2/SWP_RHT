package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.exception.PartyRuleException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PartyRolePolicyTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 23);

    @Test
    void acceptsOpenAndInclusivePeriods() {
        assertThatCode(() -> PartyRolePolicy.requireValidPeriod(START, null)).doesNotThrowAnyException();
        assertThatCode(() -> PartyRolePolicy.requireValidPeriod(START, START)).doesNotThrowAnyException();
        assertThatCode(() -> PartyRolePolicy.requireValidPeriod(START, START.plusDays(1))).doesNotThrowAnyException();
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> PartyRolePolicy.requireValidPeriod(START, START.minusDays(1)))
                .isInstanceOf(PartyRuleException.class);
    }
}
