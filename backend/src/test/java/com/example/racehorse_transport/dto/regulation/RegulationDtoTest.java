package com.example.racehorse_transport.dto.regulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.entity.enums.DocumentSubmitter;
import com.example.racehorse_transport.entity.enums.RuleDirection;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RegulationDtoTest {

    @Test
    void mapsCountryRuleAndPreservesIdOnUpdate() {
        Country country = new Country();
        country.setCode("KH");
        LocalDate start = LocalDate.of(2026, 1, 1);
        var rule = new CountryRuleCreateRequestDto("KH", RuleDirection.EXPORT, 14, 7, 7, start, null)
                .toEntity(country);
        assertThat(rule.getId()).isNull();
        assertThat(CountryRuleResponseDto.from(rule))
                .isEqualTo(new CountryRuleResponseDto(null, "KH", RuleDirection.EXPORT, 14, 7, 7, start, null));

        rule.setId(1L);
        new CountryRuleUpdateRequestDto("KH", RuleDirection.IMPORT, 30, 7, 30, start, start).applyTo(rule, country);
        assertThat(CountryRuleResponseDto.fromList(List.of(rule)))
                .containsExactly(new CountryRuleResponseDto(1L, "KH", RuleDirection.IMPORT, 30, 7, 30, start, start));
    }

    @Test
    void mapsDocumentTypeAndPreservesIdOnUpdate() {
        var type = new DocumentTypeCreateRequestDto("CERT", "Certificate", DocumentSubject.HORSE).toEntity();
        assertThat(type.getId()).isNull();
        assertThat(DocumentTypeResponseDto.from(type))
                .isEqualTo(new DocumentTypeResponseDto(null, "CERT", "Certificate", DocumentSubject.HORSE));

        type.setId(2L);
        new DocumentTypeUpdateRequestDto("PERMIT", "Permit", DocumentSubject.BOOKING).applyTo(type);
        assertThat(DocumentTypeResponseDto.fromList(List.of(type)))
                .containsExactly(new DocumentTypeResponseDto(2L, "PERMIT", "Permit", DocumentSubject.BOOKING));
    }

    @Test
    void mapsRequirementAndPreservesParentOnUpdate() {
        Country country = new Country();
        country.setCode("KH");
        var rule = new CountryRuleCreateRequestDto("KH", RuleDirection.EXPORT, 0, 0, 0,
                LocalDate.of(2026, 1, 1), null).toEntity(country);
        rule.setId(1L);
        var type = new DocumentTypeCreateRequestDto("CERT", "Certificate", DocumentSubject.HORSE).toEntity();
        type.setId(2L);
        var requirement = new DocumentRequirementCreateRequestDto(2L, null, DocumentSubmitter.COMPANY, true)
                .toEntity(rule, type, null);
        assertThat(requirement.getId()).isNull();
        assertThat(DocumentRequirementResponseDto.from(requirement))
                .isEqualTo(new DocumentRequirementResponseDto(null, 1L, 2L, "CERT", "Certificate",
                        DocumentSubject.HORSE, null, DocumentSubmitter.COMPANY, true));

        requirement.setId(3L);
        Location gate = new Location();
        gate.setId(4L);
        new DocumentRequirementUpdateRequestDto(2L, 4L, DocumentSubmitter.CUSTOMER, false)
                .applyTo(requirement, type, gate);
        assertThat(requirement.getRule()).isSameAs(rule);
        assertThat(DocumentRequirementResponseDto.fromList(List.of(requirement)))
                .containsExactly(new DocumentRequirementResponseDto(3L, 1L, 2L, "CERT", "Certificate",
                        DocumentSubject.HORSE, 4L, DocumentSubmitter.CUSTOMER, false));
    }
}
