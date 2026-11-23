package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.HorseInsurance;
import com.example.racehorse_transport.entity.Party;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record InsuranceEvidenceRequestDto(@NotNull @Positive Long insurerPartyId,
        @NotNull BigDecimal coverageAmount, BigDecimal premiumAmount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode, Instant quoteExpiresAt) {

    public void applyTo(HorseInsurance insurance, Party insurer) {
        insurance.setInsurerParty(insurer);
        insurance.setCurrencyCode(currencyCode);
        insurance.setQuoteExpiresAt(quoteExpiresAt);
    }
}
