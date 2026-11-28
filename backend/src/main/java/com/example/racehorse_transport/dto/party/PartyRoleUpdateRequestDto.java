package com.example.racehorse_transport.dto.party;

import com.example.racehorse_transport.entity.PartyRole;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PartyRoleUpdateRequestDto(@NotNull LocalDate validFrom, LocalDate validTo) {

    public void applyTo(PartyRole role) {
        role.setValidFrom(validFrom);
        role.setValidTo(validTo);
    }
}
