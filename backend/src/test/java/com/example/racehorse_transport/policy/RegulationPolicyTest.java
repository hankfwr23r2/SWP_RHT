package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.CountryRule;
import com.example.racehorse_transport.entity.DocumentRequirement;
import com.example.racehorse_transport.entity.DocumentType;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.RuleDirection;
import com.example.racehorse_transport.exception.RegulationRuleException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegulationPolicyTest {

    @Test
    void selectsPartnerCountryByDirection() {
        assertThat(RegulationPolicy.partnerCountry(BookingType.EXPORT, "VN", "KH")).isEqualTo("KH");
        assertThat(RegulationPolicy.partnerCountry(BookingType.IMPORT, "LA", "VN")).isEqualTo("LA");
        assertThat(RegulationPolicy.partnerCountry(BookingType.INTERPROVINCE, "VN", "VN")).isEqualTo("VN");
        assertThat(RegulationPolicy.partnerCountry(BookingType.LOCAL, "VN", "VN")).isNull();
    }

    @Test
    void includesBothDateBoundariesAndSupportsAnOpenEnd() {
        LocalDate start = LocalDate.of(2026, 12, 1);
        LocalDate end = start.plusDays(4);
        CountryRule rule = new CountryRule();
        rule.setValidFrom(start);
        rule.setValidTo(end);

        assertThat(RegulationPolicy.effectiveOn(rule, start.minusDays(1))).isFalse();
        assertThat(RegulationPolicy.effectiveOn(rule, start)).isTrue();
        assertThat(RegulationPolicy.effectiveOn(rule, end)).isTrue();
        assertThat(RegulationPolicy.effectiveOn(rule, end.plusDays(1))).isFalse();
        rule.setValidTo(null);
        assertThat(RegulationPolicy.effectiveOn(rule, end.plusYears(1))).isTrue();
    }

    @Test
    void sumsCalendarDaysAndChecksCutoffInVietnamTime() {
        CountryRule rule = new CountryRule();
        rule.setPermitLeadDays(14);
        rule.setCertificateLeadDays(7);
        rule.setQuarantineDays(7);
        long lead = RegulationPolicy.leadDays(rule);
        Instant now = Instant.parse("2026-11-30T18:00:00Z");
        Instant threshold = Instant.parse("2026-12-28T17:00:00Z");

        assertThat(lead).isEqualTo(28);
        assertThat(RegulationPolicy.feasible(lead, now, threshold)).isTrue();
        assertThat(RegulationPolicy.feasible(lead, now, threshold.minusSeconds(1))).isFalse();
        assertThat(RegulationPolicy.feasible(0, now, now)).isFalse();
    }

    @Test
    void matchingGateOverridesGeneralRequirementRegardlessOfOrder() {
        DocumentType type = new DocumentType();
        type.setId(1L);
        Location gate = new Location();
        gate.setId(2L);
        DocumentRequirement general = new DocumentRequirement();
        general.setDocumentType(type);
        DocumentRequirement specific = new DocumentRequirement();
        specific.setDocumentType(type);
        specific.setGateLocation(gate);

        assertThat(RegulationPolicy.requirementsForGate(List.of(general, specific), 2L)).containsExactly(specific);
        assertThat(RegulationPolicy.requirementsForGate(List.of(specific, general), 2L)).containsExactly(specific);
        assertThat(RegulationPolicy.requirementsForGate(List.of(specific, general), 3L)).containsExactly(general);
        assertThat(RegulationPolicy.requirementsForGate(List.of(specific, general), null)).containsExactly(general);
    }

    @Test
    void rejectsInvalidCountryDateAndGate() {
        LocalDate start = LocalDate.of(2026, 12, 1);
        assertThatThrownBy(() -> RegulationPolicy.validateRule("KH", RuleDirection.INTERPROVINCE, start, null))
                .isInstanceOf(RegulationRuleException.class);
        assertThatThrownBy(() -> RegulationPolicy.validateRule("VN", RuleDirection.EXPORT, start, null))
                .isInstanceOf(RegulationRuleException.class);
        assertThatThrownBy(() -> RegulationPolicy.validateRule("KH", RuleDirection.EXPORT, start, start.minusDays(1)))
                .isInstanceOf(RegulationRuleException.class);
        Location hub = new Location();
        hub.setLocationType(LocationType.HUB);
        assertThatThrownBy(() -> RegulationPolicy.validateGate(hub)).isInstanceOf(RegulationRuleException.class);
    }
}
