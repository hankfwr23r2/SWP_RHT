package com.example.racehorse_transport.dto.party;

import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.enums.PartyKind;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PartyUpdateRequestDto(
        @NotNull PartyKind partyType,
        @NotBlank @Size(max = 200) String displayName,
        @Size(max = 20) String phone,
        @Email @Size(max = 254) String email,
        @Size(max = 500) String address,
        @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
        @Size(max = 30) String taxCode) {

    public void applyTo(Party party, Country country) {
        party.setPartyType(partyType);
        party.setDisplayName(displayName.strip());
        party.setPhone(phone);
        party.setEmail(email);
        party.setAddress(address);
        party.setCountry(country);
        party.setTaxCode(taxCode);
    }
}
