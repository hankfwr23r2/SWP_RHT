package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.party.PartyCreateRequestDto;
import com.example.racehorse_transport.dto.party.PartyResponseDto;
import com.example.racehorse_transport.dto.party.PartyRoleCreateRequestDto;
import com.example.racehorse_transport.dto.party.PartyRoleResponseDto;
import com.example.racehorse_transport.dto.party.PartyRoleUpdateRequestDto;
import com.example.racehorse_transport.dto.party.PartyUpdateRequestDto;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import java.util.List;

public interface PartyService {

    List<PartyResponseDto> parties();

    PartyResponseDto party(Long id);

    PartyResponseDto createParty(PartyCreateRequestDto request);

    PartyResponseDto updateParty(Long id, PartyUpdateRequestDto request);

    PartyRoleResponseDto addRole(Long partyId, PartyRoleCreateRequestDto request);

    PartyRoleResponseDto updateRole(Long partyId, PartyRoleType roleType, PartyRoleUpdateRequestDto request);
}
