package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.HorseInsurance;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record PersonalInsuranceRevisionRequestDto(@NotBlank @Size(max = 200) String insurerName,
        @NotBlank @Size(max = 50) String policyNumber, @NotNull LocalDate validFrom, @NotNull LocalDate validTo) {

    public void applyTo(HorseInsurance insurance) {
        insurance.setInsurerName(insurerName.strip());
        insurance.setPolicyNumber(policyNumber.strip());
        insurance.setValidFrom(validFrom);
        insurance.setValidTo(validTo);
    }
}
