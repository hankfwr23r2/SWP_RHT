package com.example.racehorse_transport.dto.party;

import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.PartyRole;
import com.example.racehorse_transport.entity.PartyRoleId;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PartyRoleCreateRequestDto(@NotNull PartyRoleType roleType, @NotNull LocalDate validFrom,
                                        LocalDate validTo) {

    public PartyRole toEntity(Party party, PartyRoleId id) {
        PartyRole role = new PartyRole();
        role.setId(id);
        role.setParty(party);
        role.setValidFrom(validFrom);
        role.setValidTo(validTo);
        return role;
    }
}
