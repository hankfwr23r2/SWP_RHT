package com.example.racehorse_transport.dto.party;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.PartyRole;
import com.example.racehorse_transport.entity.PartyRoleId;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.PartyKind;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartyDtoTest {

    @Test
    void createsPartyWithResolvedCountryAndServerFieldsUnset() {
        Country country = new Country();
        var request = new PartyCreateRequestDto(PartyKind.PERSON, " Partner ", null, "vet@example.com",
                "Test address", "VN", null);

        Party party = request.toEntity(country);

        assertThat(party.getId()).isNull();
        assertThat(party.getStatus()).isNull();
        assertThat(party.getCountry()).isSameAs(country);
        assertThat(party.getDisplayName()).isEqualTo("Partner");
        assertThat(party.getEmail()).isEqualTo("vet@example.com");
    }

    @Test
    void updatesPartyAndPreservesIdAndStatus() {
        Country country = new Country();
        country.setCode("KH");
        Party party = new Party();
        party.setId(42L);
        party.setStatus(ActiveStatus.INACTIVE);
        var request = new PartyUpdateRequestDto(PartyKind.ORGANIZATION, " Company ", "123", null,
                "Address", "KH", "ABC");

        request.applyTo(party, country);

        assertThat(PartyResponseDto.from(party, List.of())).isEqualTo(
                new PartyResponseDto(42L, PartyKind.ORGANIZATION, "Company", "123", null, "Address",
                        "KH", "ABC", ActiveStatus.INACTIVE, List.of()));
    }

    @Test
    void mapsRolePeriodAndPreservesItsIdentityWhenUpdating() {
        Party party = new Party();
        party.setId(9L);
        PartyRoleId id = new PartyRoleId(9L, PartyRoleType.VET);
        LocalDate start = LocalDate.of(2026, 11, 23);
        var request = new PartyRoleCreateRequestDto(PartyRoleType.VET, start, start.plusDays(2));

        PartyRole role = request.toEntity(party, id);

        assertThat(role.getId()).isSameAs(id);
        assertThat(role.getParty()).isSameAs(party);
        assertThat(PartyRoleResponseDto.from(role)).isEqualTo(
                new PartyRoleResponseDto(PartyRoleType.VET, start, start.plusDays(2)));
        new PartyRoleUpdateRequestDto(start.plusDays(1), null).applyTo(role);
        assertThat(role.getId()).isSameAs(id);
        assertThat(role.getParty()).isSameAs(party);
        assertThat(PartyRoleResponseDto.from(role)).isEqualTo(
                new PartyRoleResponseDto(PartyRoleType.VET, start.plusDays(1), null));
    }
}
