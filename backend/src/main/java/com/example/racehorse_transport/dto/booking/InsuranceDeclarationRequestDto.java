package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.HorseInsurance;
import com.example.racehorse_transport.entity.TermsAcceptance;
import com.example.racehorse_transport.entity.enums.InsuranceCoverageOption;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InsuranceDeclarationRequestDto(
        @NotNull InsuranceCoverageOption coverageOption,
        String policyNumber,
        LocalDate validFrom,
        LocalDate validTo,
        @Size(max = 200) String insurerName,
        Boolean acceptedWaiver) {

    public HorseInsurance toEntity(BookingHorse horse, TermsAcceptance waiver) {
        HorseInsurance insurance = new HorseInsurance();
        insurance.setBookingHorse(horse);
        insurance.setCoverageOption(coverageOption);
        insurance.setWaiverTermsAcceptance(waiver);
        if (coverageOption == InsuranceCoverageOption.PERSONAL) {
            insurance.setInsurerName(insurerName == null || insurerName.isBlank() ? null : insurerName.trim());
            insurance.setPolicyNumber(policyNumber.trim());
            insurance.setValidFrom(validFrom);
            insurance.setValidTo(validTo);
        }
        return insurance;
    }
}
