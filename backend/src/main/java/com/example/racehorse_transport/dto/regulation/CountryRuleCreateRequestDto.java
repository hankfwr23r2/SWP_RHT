package com.example.racehorse_transport.dto.regulation;

import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.CountryRule;
import com.example.racehorse_transport.entity.enums.RuleDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public record CountryRuleCreateRequestDto(
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode,
        @NotNull RuleDirection direction,
        @NotNull @PositiveOrZero Integer permitLeadDays,
        @NotNull @PositiveOrZero Integer certificateLeadDays,
        @NotNull @PositiveOrZero Integer quarantineDays,
        @NotNull LocalDate validFrom,
        LocalDate validTo) {

    public CountryRule toEntity(Country country) {
        CountryRule entity = new CountryRule();
        entity.setCountry(country);
        entity.setDirection(direction);
        entity.setPermitLeadDays(permitLeadDays);
        entity.setCertificateLeadDays(certificateLeadDays);
        entity.setQuarantineDays(quarantineDays);
        entity.setValidFrom(validFrom);
        entity.setValidTo(validTo);
        return entity;
    }
}
