package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.operation.UnclaimedHorseResolutionRequestDto;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.repository.HandoverHorseRepository;
import com.example.racehorse_transport.repository.HandoverRepository;
import com.example.racehorse_transport.service.HandoverEvidenceService;
import com.example.racehorse_transport.service.HorseStayService;
import com.example.racehorse_transport.service.UnclaimedHandoverService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UnclaimedHandoverServiceImpl implements UnclaimedHandoverService {
    private final HandoverRepository handovers;
    private final HandoverHorseRepository scans;
    private final HandoverEvidenceService evidence;
    private final HorseStayService stays;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(HorseStay stay, HorseLeg failed, Staff manager, UnclaimedHorseResolutionRequestDto request,
                       MultipartFile proof, Instant now) {
        var handover = request.toEntity(stay, manager);
        handover.setStatus(HandoverStatus.DONE);
        handover.setHandedAt(now);
        handovers.saveAndFlush(handover);
        var row = new HandoverHorse();
        row.setId(new HandoverHorseId(handover.getId(), failed.getId()));
        row.setHandover(handover);
        row.setHorseLeg(failed);
        row.setMicrochipMatched(false);
        row.setRecipientName(request.recipientName().strip());
        scans.save(row);
        evidence.attachRecord(handover.getId(), proof, manager.getUserId());
        stays.closeActive(stay.getBookingHorse().getId(), now, manager.getUserId());
    }
}
