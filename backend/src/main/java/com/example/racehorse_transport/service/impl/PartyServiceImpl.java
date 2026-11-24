package com.example.racehorse_transport.service.impl;

import java.time.Clock;
import com.example.racehorse_transport.policy.BookingSubmissionPolicy;
import com.example.racehorse_transport.dto.party.InsurerResponseDto;
import com.example.racehorse_transport.dto.party.PartyCreateRequestDto;
import com.example.racehorse_transport.dto.party.PartyResponseDto;
import com.example.racehorse_transport.dto.party.PartyRoleCreateRequestDto;
import com.example.racehorse_transport.dto.party.PartyRoleResponseDto;
import com.example.racehorse_transport.dto.party.PartyRoleUpdateRequestDto;
import com.example.racehorse_transport.dto.party.PartyUpdateRequestDto;
import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.PartyRole;
import com.example.racehorse_transport.entity.PartyRoleId;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import com.example.racehorse_transport.exception.PartyRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.PartyRolePolicy;
import com.example.racehorse_transport.repository.CountryRepository;
import com.example.racehorse_transport.repository.PartyRepository;
import com.example.racehorse_transport.repository.PartyRoleRepository;
import com.example.racehorse_transport.service.PartyService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PartyServiceImpl implements PartyService {

    private final PartyRepository parties;
    private final PartyRoleRepository roles;
    private final CountryRepository countries;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<PartyResponseDto> parties() {
        List<Party> rows = parties.findAllByOrderByDisplayNameAscIdAsc();
        Map<Long, List<PartyRoleResponseDto>> rolesById = roles.findById_PartyIdInOrderById_RoleType(
                        rows.stream().map(Party::getId).toList()).stream()
                .collect(Collectors.groupingBy(role -> role.getId().getPartyId(),
                        Collectors.mapping(PartyRoleResponseDto::from, Collectors.toList())));
        return rows.stream()
                .map(party -> PartyResponseDto.from(party, rolesById.getOrDefault(party.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InsurerResponseDto> insurers() {
        return parties.findActiveByRole(PartyRoleType.INSURER, BookingSubmissionPolicy.businessDate(clock.instant())).stream()
                .map(InsurerResponseDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PartyResponseDto party(Long id) {
        Party party = parties.findOneById(id).orElseThrow(() -> missingParty(id));
        return responseWithRoles(party);
    }

    @Override
    @Transactional
    public PartyResponseDto createParty(PartyCreateRequestDto request) {
        Party party = request.toEntity(findCountry(request.countryCode()));
        party.setStatus(ActiveStatus.ACTIVE);
        return PartyResponseDto.from(parties.save(party), List.of());
    }

    @Override
    @Transactional
    public PartyResponseDto updateParty(Long id, PartyUpdateRequestDto request) {
        Party party = lockParty(id);
        request.applyTo(party, findCountry(request.countryCode()));
        return responseWithRoles(party);
    }

    @Override
    @Transactional
    public PartyRoleResponseDto addRole(Long partyId, PartyRoleCreateRequestDto request) {
        PartyRolePolicy.requireValidPeriod(request.validFrom(), request.validTo());
        Party party = lockParty(partyId);
        PartyRolePolicy.requireAssignableRole(request.roleType());
        PartyRoleId roleId = new PartyRoleId(partyId, request.roleType());
        if (roles.existsById(roleId)) {
            throw new PartyRuleException.DuplicateRoleException();
        }
        return PartyRoleResponseDto.from(roles.save(request.toEntity(party, roleId)));
    }

    @Override
    @Transactional
    public PartyRoleResponseDto updateRole(Long partyId, PartyRoleType roleType, PartyRoleUpdateRequestDto request) {
        PartyRolePolicy.requireValidPeriod(request.validFrom(), request.validTo());
        lockParty(partyId);
        PartyRole role = roles.findById(new PartyRoleId(partyId, roleType))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò của đối tác."));
        request.applyTo(role);
        return PartyRoleResponseDto.from(role);
    }

    private PartyResponseDto responseWithRoles(Party party) {
        List<PartyRoleResponseDto> assignedRoles = roles.findById_PartyIdInOrderById_RoleType(List.of(party.getId()))
                .stream().map(PartyRoleResponseDto::from).toList();
        return PartyResponseDto.from(party, assignedRoles);
    }

    private Party lockParty(Long id) {
        return parties.findForUpdate(id).orElseThrow(() -> missingParty(id));
    }

    private ResourceNotFoundException missingParty(Long id) {
        return new ResourceNotFoundException("Không tìm thấy đối tác " + id);
    }

    private Country findCountry(String countryCode) {
        return countries.findById(countryCode.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new PartyRuleException("Quốc gia không hợp lệ."));
    }
}
