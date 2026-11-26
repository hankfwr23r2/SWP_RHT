package com.example.racehorse_transport.dto.party;

import com.example.racehorse_transport.entity.PartyRole;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import java.time.LocalDate;

public record PartyRoleResponseDto(PartyRoleType roleType, LocalDate validFrom, LocalDate validTo) {

    public static PartyRoleResponseDto from(PartyRole role) {
        return new PartyRoleResponseDto(role.getId().getRoleType(), role.getValidFrom(), role.getValidTo());
    }
}
