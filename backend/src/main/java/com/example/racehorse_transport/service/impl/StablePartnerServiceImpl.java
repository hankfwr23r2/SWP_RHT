package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.health.StablePartnerResponseDto;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.DispatchPolicy;
import com.example.racehorse_transport.repository.PartyRepository;
import com.example.racehorse_transport.repository.PartyRoleRepository;
import com.example.racehorse_transport.service.StablePartnerService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StablePartnerServiceImpl implements StablePartnerService {
    private final PartyRepository parties;
    private final PartyRoleRepository partyRoles;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Party requireStablePartner(Long partyId) {
        Party party = parties.findById(partyId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đối tác " + partyId));
        if (!isStable(party.getId())) {
            throw new DispatchRuleException("partyId", party.getDisplayName() + " không có vai trò chuồng gửi còn hiệu lực.");
        }
        return party;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StablePartnerResponseDto> stablePartners() {
        return parties.findAll().stream().filter(p -> isStable(p.getId()))
                .sorted(Comparator.comparing(Party::getDisplayName)).map(StablePartnerResponseDto::from).toList();
    }

    private boolean isStable(Long partyId) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        return partyRoles.findById_PartyId(partyId).stream()
                .anyMatch(r -> r.getId().getRoleType() == PartyRoleType.STABLE && DispatchPolicy.roleValidOn(r.getValidFrom(), r.getValidTo(), today));
    }
}
