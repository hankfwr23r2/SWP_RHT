package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.health.StablePartnerResponseDto;
import com.example.racehorse_transport.entity.Party;
import java.util.List;

public interface StablePartnerService {
    Party requireStablePartner(Long partyId);

    List<StablePartnerResponseDto> stablePartners();
}
