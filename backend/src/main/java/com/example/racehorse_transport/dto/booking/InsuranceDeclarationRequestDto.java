package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.HorseInsurance;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.TermsAcceptance;
import com.example.racehorse_transport.entity.enums.InsuranceCoverageOption;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InsuranceDeclarationRequestDto(
        @NotNull InsuranceCoverageOption coverageOption,
        String policyNumber,
        LocalDate validFrom,
        LocalDate validTo,
        Long insurerPartyId,
        Boolean acceptedWaiver) {

    public HorseInsurance toEntity(BookingHorse horse, Party insurer, TermsAcceptance waiver) {
        HorseInsurance insurance = new HorseInsurance();
        insurance.setBookingHorse(horse);
        insurance.setCoverageOption(coverageOption);
        insurance.setWaiverTermsAcceptance(waiver);
        if (coverageOption == InsuranceCoverageOption.PERSONAL) {
            insurance.setInsurerParty(insurer);
            insurance.setPolicyNumber(policyNumber.trim());
            insurance.setValidFrom(validFrom);
            insurance.setValidTo(validTo);
        }
        return insurance;
    }
}
