package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.HorseInsurance;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record InsuranceEvidenceRequestDto(@Size(max = 200) String insurerName,
        @NotNull BigDecimal coverageAmount, BigDecimal premiumAmount, Instant quoteExpiresAt) {

    public void applyTo(HorseInsurance insurance) {
        if (insurerName != null) {
            insurance.setInsurerName(insurerName.isBlank() ? null : insurerName.trim());
        }
        insurance.setCurrencyCode("VND");
        insurance.setQuoteExpiresAt(quoteExpiresAt);
    }
}
