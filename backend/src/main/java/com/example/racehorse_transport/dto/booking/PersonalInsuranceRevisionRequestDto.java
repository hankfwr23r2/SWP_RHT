package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.HorseInsurance;
import com.example.racehorse_transport.entity.Party;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record PersonalInsuranceRevisionRequestDto(@NotNull @Positive Long insurerPartyId,
        @NotBlank @Size(max = 50) String policyNumber, @NotNull LocalDate validFrom, @NotNull LocalDate validTo) {

    public void applyTo(HorseInsurance insurance, Party insurer) {
        insurance.setInsurerParty(insurer);
        insurance.setPolicyNumber(policyNumber.strip());
        insurance.setValidFrom(validFrom);
        insurance.setValidTo(validTo);
    }
}
