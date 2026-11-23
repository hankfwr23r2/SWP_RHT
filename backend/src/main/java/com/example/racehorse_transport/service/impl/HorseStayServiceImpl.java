package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.HorseStayRepository;
import com.example.racehorse_transport.service.HorseStayService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HorseStayServiceImpl implements HorseStayService {

    // Chưa có chặng sau thì dự kiến tập kết tối đa một đêm (docs/NGHIEP_VU_THEO_59_BANG.md mục 1.2)
    private static final Duration DEFAULT_ASSEMBLY = Duration.ofHours(24);

    private final HorseStayRepository stays;
    private final HorseLegRepository legs;

    @Override
    @Transactional
    public void openAssembly(HorseLeg alightedLeg, Instant at) {
        Instant plannedEnd = legs.findByBookingHorse_IdOrderBySequenceNumber(alightedLeg.getBookingHorse().getId()).stream()
                .filter(l -> l.getSequenceNumber() > alightedLeg.getSequenceNumber() && l.getStatus() == HorseLegStatus.PLANNED)
                .map(HorseLeg::getPlannedStartAt).filter(start -> start.isAfter(at))
                .findFirst().orElse(at.plus(DEFAULT_ASSEMBLY));
        HorseStay stay = new HorseStay();
        stay.setBookingHorse(alightedLeg.getBookingHorse());
        stay.setStayType(HorseStayType.ASSEMBLY);
        stay.setLocation(alightedLeg.getAlightTripStop().getLocation());
        stay.setPlannedStartAt(at);
        stay.setPlannedEndAt(plannedEnd);
        stay.setActualStartAt(at);
        stays.save(stay);
    }

    @Override
    @Transactional
    public void closeActive(Long bookingHorseId, Instant at) {
        stays.findFirstByBookingHorse_IdAndActualStartAtIsNotNullAndActualEndAtIsNull(bookingHorseId).ifPresent(stay -> stay.setActualEndAt(at));
    }

    @Override
    @Transactional(readOnly = true)
    public List<HorseStayResponseDto> activeAt(Long locationId) {
        return stays.findByLocation_IdAndActualStartAtIsNotNullAndActualEndAtIsNullOrderByActualStartAt(locationId).stream()
                .map(HorseStayResponseDto::from).toList();
    }
}
