package com.example.racehorse_transport.dto.party;

import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.PartyKind;
import java.util.List;

public record PartyResponseDto(Long id, PartyKind partyType, String displayName, String phone, String email,
                               String address, String countryCode, String taxCode, ActiveStatus status,
                               List<PartyRoleResponseDto> roles) {

    public static PartyResponseDto from(Party party, List<PartyRoleResponseDto> roles) {
        return new PartyResponseDto(party.getId(), party.getPartyType(), party.getDisplayName(), party.getPhone(),
                party.getEmail(), party.getAddress(), party.getCountry().getCode(), party.getTaxCode(),
                party.getStatus(), roles);
    }
}
