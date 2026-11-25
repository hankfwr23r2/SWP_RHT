package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import com.example.racehorse_transport.policy.HubStallPolicy;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.service.CoordinatorRecipientService;
import com.example.racehorse_transport.service.HorseStayQueryService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HorseStayQueryServiceImpl implements HorseStayQueryService {
    private final HorseStayRepository stays;
    private final CoordinatorRecipientService coordinators;

    @Override
    @Transactional(readOnly = true)
    public List<HorseStayResponseDto> activeAt(Long locationId) {
        var unpaid = Set.copyOf(stays.findUnpaidAssemblyStayIds(locationId));
        var sick = Set.copyOf(stays.findStaysWithSickHorses(locationId));
        return stays.findByLocation_IdAndActualStartAtIsNotNullAndActualEndAtIsNullOrderByActualStartAt(locationId).stream()
                .map(stay -> HorseStayResponseDto.from(stay, stay.getBalanceHoldAt() != null || unpaid.contains(stay.getId()),
                        HubStallPolicy.isCompanyHubStay(stay), HubStallPolicy.suggestIsolation(stay, sick.contains(stay.getId())),
                        coordinators.atHub(stay.getBookingHorse())))
                .toList();
    }
}
