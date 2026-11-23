package com.example.racehorse_transport.dto.regulation;

import com.example.racehorse_transport.entity.CountryRule;
import com.example.racehorse_transport.entity.enums.RuleDirection;
import java.time.LocalDate;
import java.util.List;

public record CountryRuleResponseDto(
        Long id, String countryCode, RuleDirection direction, Integer permitLeadDays,
        Integer certificateLeadDays, Integer quarantineDays, LocalDate validFrom, LocalDate validTo) {

    public static CountryRuleResponseDto from(CountryRule entity) {
        return new CountryRuleResponseDto(entity.getId(), entity.getCountry().getCode(), entity.getDirection(),
                entity.getPermitLeadDays(), entity.getCertificateLeadDays(), entity.getQuarantineDays(),
                entity.getValidFrom(), entity.getValidTo());
    }

    public static List<CountryRuleResponseDto> fromList(List<CountryRule> entities) {
        return entities.stream().map(CountryRuleResponseDto::from).toList();
    }
}
