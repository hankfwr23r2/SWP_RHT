package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.dto.party.PartyCreateRequestDto;
import com.example.racehorse_transport.dto.party.PartyRoleCreateRequestDto;
import com.example.racehorse_transport.dto.party.PartyRoleUpdateRequestDto;
import com.example.racehorse_transport.dto.party.PartyUpdateRequestDto;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.PartyKind;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import com.example.racehorse_transport.exception.PartyRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@Transactional
class PartyServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 23);

    @Autowired private PartyService parties;

    @Test
    void createsAndUpdatesPartyAndMaintainsItsRoles() {
        var created = parties.createParty(new PartyCreateRequestDto(
                PartyKind.PERSON, " Test vet ", null, null, null, "vn", null));
        assertThat(created.displayName()).isEqualTo("Test vet");
        assertThat(created.status()).isEqualTo(ActiveStatus.ACTIVE);
        assertThat(created.roles()).isEmpty();

        parties.addRole(created.id(), new PartyRoleCreateRequestDto(PartyRoleType.VET, START, null));
        var updatedRole = parties.updateRole(created.id(), PartyRoleType.VET,
                new PartyRoleUpdateRequestDto(START, START.plusDays(2)));
        var updated = parties.updateParty(created.id(), new PartyUpdateRequestDto(
                PartyKind.PERSON, "Updated vet", "1234", null, null, "KH", null));

        assertThat(updated.roles()).containsExactly(updatedRole);
        assertThat(updated.countryCode()).isEqualTo("KH");
        assertThat(parties.party(created.id())).isEqualTo(updated);
        assertThat(parties.parties()).contains(updated);
    }

    @Test
    void rejectsUnknownCountry() {
        assertThatThrownBy(() -> parties.createParty(new PartyCreateRequestDto(
                PartyKind.PERSON, "Invalid country", null, null, null, "ZZ", null)))
                .isInstanceOf(PartyRuleException.class);
    }

    @Test
    void rejectsDuplicateRolesAndInvalidPeriods() {
        var created = parties.createParty(new PartyCreateRequestDto(
                PartyKind.PERSON, "Role test", null, null, null, "VN", null));
        var role = new PartyRoleCreateRequestDto(PartyRoleType.VET, START, null);
        parties.addRole(created.id(), role);

        assertThatThrownBy(() -> parties.addRole(created.id(), role))
                .isInstanceOf(PartyRuleException.DuplicateRoleException.class);
        assertThatThrownBy(() -> parties.updateRole(created.id(), PartyRoleType.VET,
                new PartyRoleUpdateRequestDto(START, START.minusDays(1)))).isInstanceOf(PartyRuleException.class);
        assertThat(parties.party(created.id()).roles()).singleElement().satisfies(saved ->
                assertThat(saved.validTo()).isNull());
    }

    @Test
    void rejectsMissingPartiesAndRoles() {
        assertThatThrownBy(() -> parties.party(Long.MAX_VALUE)).isInstanceOf(ResourceNotFoundException.class);
        var created = parties.createParty(new PartyCreateRequestDto(
                PartyKind.PERSON, "Missing role test", null, null, null, "VN", null));
        assertThatThrownBy(() -> parties.updateRole(created.id(), PartyRoleType.INSURER,
                new PartyRoleUpdateRequestDto(START, null))).isInstanceOf(ResourceNotFoundException.class);
    }
}
