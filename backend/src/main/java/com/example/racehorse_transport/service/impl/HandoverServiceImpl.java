package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.operation.CrewTripResponseDto;
import com.example.racehorse_transport.dto.operation.HandoverCreateRequestDto;
import com.example.racehorse_transport.dto.operation.HandoverRefusalRequestDto;
import com.example.racehorse_transport.dto.operation.HandoverResponseDto;
import com.example.racehorse_transport.dto.operation.HandoverScanRequestDto;
import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.HandoverHorseId;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.HandoverHorseRepository;
import com.example.racehorse_transport.repository.HandoverRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.service.DispatchService;
import com.example.racehorse_transport.service.HandoverOutcomeService;
import com.example.racehorse_transport.service.HandoverService;
import com.example.racehorse_transport.service.HorseLegService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HandoverServiceImpl implements HandoverService {

    private final HandoverRepository handovers;
    private final HandoverHorseRepository scans;
    private final HorseLegRepository legs;
    private final TripStopRepository stops;
    private final CrewAssignmentRepository crew;
    private final DispatchService dispatch;
    private final HorseLegService horseLegs;
    private final HandoverOutcomeService outcomes;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public CrewTripResponseDto crewTrip(Long vehicleTripId, String username) {
        requireCrew(vehicleTripId, username);
        List<Long> stopIds = stops.findByVehicleTrip_IdOrderBySequenceNumber(vehicleTripId).stream().map(TripStop::getId).toList();
        List<HandoverResponseDto> done = handovers.findByTripStop_IdInOrderById(stopIds).stream().map(this::describe).toList();
        return new CrewTripResponseDto(dispatch.trip(vehicleTripId), horseLegs.horsesOnTrip(vehicleTripId), done);
    }

    @Override
    @Transactional
    public CrewTripResponseDto create(Long tripStopId, HandoverCreateRequestDto request, String username) {
        TripStop stop = stops.findById(tripStopId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm dừng " + tripStopId));
        Long tripId = stop.getVehicleTrip().getId();
        CrewAssignment member = requireCrew(tripId, username);
        TripExecutionPolicy.requireInProgress(stop.getVehicleTrip().getStatus());
        if (stop.getActualArrivalAt() == null || stop.getActualDepartureAt() != null) {
            throw new DispatchRuleException("tripStopId", "Chỉ giao nhận khi xe đang ở điểm dừng này.", true);
        }
        TripExecutionPolicy.requireHandoverFitsStop(request.handoverType(), stop.getStopType());
        if (request.handoverType() == HandoverType.RETURN) {
            returnUnpaid(stop, request, member);
            return crewTrip(tripId, username);
        }
        Handover handover = request.toEntity(stop, member.getStaff());
        if (candidates(handover).isEmpty()) {
            throw new DispatchRuleException("handoverType", "Không có con ngựa nào cần giao nhận loại này ở điểm dừng này.", true);
        }
        handover.setStatus(HandoverStatus.PENDING);
        handovers.saveAndFlush(handover);
        return crewTrip(tripId, username);
    }

    @Override
    @Transactional
    public CrewTripResponseDto scan(Long handoverId, HandoverScanRequestDto request, String username) {
        Handover handover = pending(handoverId, username);
        HorseLeg leg = candidates(handover).stream().filter(l -> l.getId().equals(request.horseLegId())).findFirst()
                .orElseThrow(() -> new DispatchRuleException("horseLegId", "Con ngựa này không thuộc lần giao nhận.", true));
        HandoverHorse scan = scans.findById(new HandoverHorseId(handover.getId(), leg.getId())).orElseGet(() -> {
            HandoverHorse created = new HandoverHorse();
            created.setId(new HandoverHorseId(handover.getId(), leg.getId()));
            created.setHandover(handover);
            created.setHorseLeg(leg);
            return created;
        });
        scan.setScannedMicrochip(request.microchip().strip());
        scan.setMicrochipMatched(TripExecutionPolicy.chipMatches(request.microchip(), leg.getBookingHorse().getHorse().getMicrochip()));
        scan.setConditionNote(request.conditionNote() == null || request.conditionNote().isBlank() ? null : request.conditionNote().strip());
        scans.saveAndFlush(scan);
        return crewTrip(handover.getTripStop().getVehicleTrip().getId(), username);
    }

    @Override
    @Transactional
    public CrewTripResponseDto complete(Long handoverId, String username) {
        Handover handover = pending(handoverId, username);
        Long tripId = handover.getTripStop().getVehicleTrip().getId();
        Long actorId = requireCrew(tripId, username).getStaff().getUserId();
        // Con đã bị từ chối nhận (chặng đã hủy) không còn trong lần giao nhận
        List<HandoverHorse> results = scans.findById_HandoverId(handover.getId()).stream()
                .filter(r -> r.getHorseLeg().getStatus() != HorseLegStatus.CANCELLED).toList();
        TripExecutionPolicy.requireSomeMatched(results.stream().map(HandoverHorse::getMicrochipMatched).toList());
        handover.setStatus(HandoverStatus.DONE);
        handover.setHandedAt(clock.instant());
        outcomes.complete(handover, results.stream().filter(r -> Boolean.TRUE.equals(r.getMicrochipMatched()))
                .map(HandoverHorse::getHorseLeg).toList(), actorId);
        return crewTrip(tripId, username);
    }

    @Override
    @Transactional
    public CrewTripResponseDto refuse(Long handoverId, HandoverRefusalRequestDto request, String username) {
        Handover handover = pending(handoverId, username);
        Long tripId = handover.getTripStop().getVehicleTrip().getId();
        Long actorId = requireCrew(tripId, username).getStaff().getUserId();
        if (handover.getHandoverType() != HandoverType.PICKUP) {
            throw new DispatchRuleException("handoverType", "Chỉ từ chối nhận ngựa khi nhận ngựa từ khách.", true);
        }
        HorseLeg leg = candidates(handover).stream().filter(l -> l.getId().equals(request.horseLegId())).findFirst()
                .orElseThrow(() -> new DispatchRuleException("horseLegId", "Con ngựa này không thuộc lần giao nhận.", true));
        outcomes.refuse(leg, request.reason().strip(), actorId);
        // Không còn con nào để nhận thì lần giao nhận kết thúc là không thành, xe được rời điểm dừng
        if (candidates(handover).isEmpty()) {
            handover.setStatus(HandoverStatus.FAILED);
            handover.setHandedAt(clock.instant());
            handovers.flush();
        }
        return crewTrip(tripId, username);
    }

    // Quá hạn trả số dư (#32): trả mọi con đã nhận lên xe ở điểm đón này cho người gửi trong một thao tác.
    // Không quét lại chip: ghi lại chip đã quét đúng lúc nhận ngựa vài phút trước
    private void returnUnpaid(TripStop stop, HandoverCreateRequestDto request, CrewAssignment member) {
        List<HorseLeg> boarded = legs.findByBoardTripStop_IdAndStatusIn(stop.getId(), Set.of(HorseLegStatus.IN_PROGRESS));
        if (boarded.isEmpty()) {
            throw new DispatchRuleException("handoverType", "Không có con ngựa nào cần giao nhận loại này ở điểm dừng này.", true);
        }
        var now = clock.instant();
        outcomes.returnUnpaid(boarded, now, member.getStaff().getUserId());
        Handover handover = request.toEntity(stop, member.getStaff());
        handover.setStatus(HandoverStatus.DONE);
        handover.setHandedAt(now);
        handovers.saveAndFlush(handover);
        for (HorseLeg leg : boarded) {
            HandoverHorse returned = new HandoverHorse();
            returned.setId(new HandoverHorseId(handover.getId(), leg.getId()));
            returned.setHandover(handover);
            returned.setHorseLeg(leg);
            returned.setScannedMicrochip(scans.findFirstByHorseLeg_IdAndHandover_HandoverTypeAndMicrochipMatchedTrue(leg.getId(), HandoverType.PICKUP)
                    .map(HandoverHorse::getScannedMicrochip).orElse(null));
            returned.setMicrochipMatched(true);
            scans.save(returned);
        }
        scans.flush();
    }

    // Con cần giao nhận: lên xe ở điểm này (đón, ra hub) hoặc xuống xe ở điểm này (giao, vào hub)
    private List<HorseLeg> candidates(Handover handover) {
        Long stopId = handover.getTripStop().getId();
        return TripExecutionPolicy.isBoarding(handover.getHandoverType())
                ? legs.findByBoardTripStop_IdAndStatusIn(stopId, Set.of(HorseLegStatus.PLANNED, HorseLegStatus.IN_PROGRESS))
                : legs.findByAlightTripStop_IdAndStatusIn(stopId, Set.of(HorseLegStatus.IN_PROGRESS, HorseLegStatus.COMPLETED));
    }

    private HandoverResponseDto describe(Handover handover) {
        Map<Long, HandoverHorse> byLeg = scans.findById_HandoverId(handover.getId()).stream()
                .collect(Collectors.toMap(s -> s.getId().getHorseLegId(), s -> s));
        // Lần trả ngựa đã xong: các con là các con đã ghi trong lần đó (chặng đã hủy)
        List<HorseLeg> horses = handover.getHandoverType() == HandoverType.RETURN
                ? byLeg.values().stream().map(HandoverHorse::getHorseLeg).toList() : candidates(handover);
        return HandoverResponseDto.from(handover, horses, byLeg);
    }

    private Handover pending(Long handoverId, String username) {
        Handover handover = handovers.findForUpdate(handoverId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lần giao nhận " + handoverId));
        requireCrew(handover.getTripStop().getVehicleTrip().getId(), username);
        if (handover.getStatus() != HandoverStatus.PENDING) {
            throw new DispatchRuleException("status", "Lần giao nhận đã kết thúc.", true);
        }
        if (handover.getTripStop().getVehicleTrip().getStatus() != VehicleTripStatus.IN_PROGRESS) {
            throw new DispatchRuleException("status", "Lượt xe chưa xuất phát hoặc đã kết thúc.", true);
        }
        return handover;
    }

    private CrewAssignment requireCrew(Long tripId, String username) {
        return crew.findFirstByVehicleTrip_IdAndStaff_User_UsernameAndStatusNot(tripId, username, CrewAssignmentStatus.RELEASED)
                .orElseThrow(() -> new AccessDeniedException("Bạn không ở trong tổ lái của lượt xe này."));
    }
}
