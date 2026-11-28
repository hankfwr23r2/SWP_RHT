package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.operation.CrewTripResponseDto;
import com.example.racehorse_transport.dto.operation.HandoverCompleteRequestDto;
import com.example.racehorse_transport.dto.operation.HandoverCreateRequestDto;
import com.example.racehorse_transport.dto.operation.HandoverRefusalRequestDto;
import org.springframework.web.multipart.MultipartFile;
import com.example.racehorse_transport.dto.operation.HandoverResponseDto;
import com.example.racehorse_transport.dto.operation.HandoverScanRequestDto;
import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.HandoverHorseId;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.MicrochipEntryMethod;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
import com.example.racehorse_transport.service.HandoverEvidenceService;
import com.example.racehorse_transport.repository.HandoverHorseRepository;
import com.example.racehorse_transport.repository.HandoverRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.service.DispatchService;
import com.example.racehorse_transport.service.HandoverCustodyService;
import com.example.racehorse_transport.service.HandoverOutcomeService;
import com.example.racehorse_transport.service.HandoverService;
import com.example.racehorse_transport.service.HorseLegService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HandoverServiceImpl implements HandoverService {


    private final HandoverRepository handovers;
    private final HandoverHorseRepository scans;
    private final HorseLegRepository legs;
    private final TripStopRepository stops;
    private final HandoverCustodyService custody;
    private final DispatchService dispatch;
    private final HorseLegService horseLegs;
    private final HandoverOutcomeService outcomes;
    private final HandoverEvidenceService evidence;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public CrewTripResponseDto crewTrip(Long vehicleTripId, String username) {
        CrewAssignment member = custody.requireCrew(vehicleTripId, username);
        var tripStops = stops.findByVehicleTrip_IdOrderBySequenceNumber(vehicleTripId);
        List<Long> stopIds = tripStops.stream().map(TripStop::getId).toList();
        var unpaidReturnStopIds = tripStops.stream()
                .filter(stop -> stop.getStopType() == com.example.racehorse_transport.entity.enums.TripStopType.PICKUP)
                .filter(stop -> stop.getActualArrivalAt() != null && stop.getActualDepartureAt() == null)
                .map(TripStop::getId)
                .filter(id -> outcomes.canReturnUnpaid(legs.findByBoardTripStop_IdAndStatusIn(
                        id, Set.of(HorseLegStatus.IN_PROGRESS))))
                .toList();
        List<Long> shiftHandoverStopIds = member.getCrewRole() == CrewRole.ESCORT
                && member.getVehicleTrip().getStatus() == VehicleTripStatus.IN_PROGRESS
                ? tripStops.stream()
                        .filter(stop -> stop.getActualArrivalAt() != null && stop.getActualDepartureAt() == null)
                        .filter(stop -> !custody.shiftAtStop(stop, member.getStaff().getUserId()).eligible().isEmpty())
                        .map(TripStop::getId).toList()
                : List.of();
        List<HandoverResponseDto> done = handovers.findByTripStop_IdInOrderById(stopIds).stream().map(this::describe).toList();
        return new CrewTripResponseDto(dispatch.trip(vehicleTripId), horseLegs.horsesOnTrip(vehicleTripId), done,
                unpaidReturnStopIds, shiftHandoverStopIds);
    }

    @Override
    @Transactional
    public CrewTripResponseDto create(Long tripStopId, HandoverCreateRequestDto request, Long incidentPlanId, String username) {
        TripStop stop = stops.findById(tripStopId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm dừng " + tripStopId));
        Long tripId = stop.getVehicleTrip().getId();
        CrewAssignment member = custody.requireCrew(tripId, username);
        boolean stable = TripExecutionPolicy.isStableHandover(request.handoverType());
        if (incidentPlanId != null && request.handoverType() != HandoverType.STAY_IN) {
            throw new DispatchRuleException("incidentPlanId", "Chỉ gắn phương án sự cố khi gửi ngựa vào chuồng.");
        }
        if (!stable) {
            custody.lockCurrentStop(stop);
            TripExecutionPolicy.requireHandoverFitsStop(request.handoverType(), stop.getStopType());
        }
        if (request.handoverType() == HandoverType.RETURN) {
            returnUnpaid(stop, request, member);
            return crewTrip(tripId, username);
        }
        if (request.handoverType() == HandoverType.PICKUP && (handovers.existsByTripStop_IdAndStatusAndHandoverTypeIn(
                tripStopId, HandoverStatus.PENDING, Set.of(HandoverType.PICKUP)) || pickupAlreadyResolved(tripStopId))) {
            return crewTrip(tripId, username);
        }
        if (request.handoverType() == HandoverType.SHIFT) {
            requireShiftEscort(member);
            var pendingShifts = handovers.findByTripStop_IdAndHandoverTypeAndStatusOrderById(tripStopId,
                    HandoverType.SHIFT, HandoverStatus.PENDING);
            if (!pendingShifts.isEmpty()) {
                requireShiftReceiver(pendingShifts.getFirst(), member);
                return crewTrip(tripId, username);
            }
        }
        Handover handover = request.toEntity(stop, member.getStaff());
        if (stable) {
            custody.prepareStableHandover(handover, request.partyId(), incidentPlanId);
        }
        List<HorseLeg> candidates = candidates(handover);
        if (candidates.isEmpty()) {
            throw new DispatchRuleException("handoverType", "Không có con ngựa nào cần giao nhận loại này ở điểm dừng này.", true);
        }
        if (handover.getHandoverType() == HandoverType.HUB_OUT && candidates.stream()
                .map(leg -> leg.getBookingHorse().getBooking().getId()).distinct().noneMatch(outcomes::canBoardAtHub)) {
            throw new DispatchRuleException("payment", "Các đơn chờ tại hub chưa trả đủ số dư.", true);
        }
        handover.setStatus(HandoverStatus.PENDING);
        handovers.saveAndFlush(handover);
        return crewTrip(tripId, username);
    }

    @Override
    @Transactional
    public CrewTripResponseDto scan(Long handoverId, HandoverScanRequestDto request, String username) {
        Handover handover = pending(handoverId, username);
        var entryMethod = Objects.requireNonNullElse(request.entryMethod(), MicrochipEntryMethod.SCAN);
        String manualReason = TripExecutionPolicy.manualEntryReason(entryMethod, request.manualReason());
        HorseLeg leg = candidates(handover).stream().filter(l -> l.getId().equals(request.horseLegId())).findFirst()
                .orElseThrow(() -> new DispatchRuleException("horseLegId", "Con ngựa này không thuộc lần giao nhận.", true));
        if (handover.getHandoverType() == HandoverType.HUB_OUT) {
            var booking = leg.getBookingHorse().getBooking();
            if (!outcomes.canBoardAtHub(booking.getId())) {
                throw new DispatchRuleException("payment", "Đơn " + booking.getCode() + " chưa trả đủ số dư.", true);
            }
        }
        HandoverHorse scan = scans.findById(new HandoverHorseId(handover.getId(), leg.getId())).orElseGet(() -> {
            HandoverHorse created = new HandoverHorse();
            created.setId(new HandoverHorseId(handover.getId(), leg.getId()));
            created.setHandover(handover);
            created.setHorseLeg(leg);
            return created;
        });
        request.applyTo(scan, entryMethod, manualReason);
        scan.setMicrochipMatched(TripExecutionPolicy.chipMatches(request.microchip(), leg.getBookingHorse().getHorse().getMicrochip()));
        scans.saveAndFlush(scan);
        return crewTrip(handover.getTripStop().getVehicleTrip().getId(), username);
    }

    @Override
    @Transactional
    public CrewTripResponseDto complete(Long handoverId, HandoverCompleteRequestDto request, String username) {
        CrewAssignment member = custody.lockForCompletion(handoverId, username);
        Long actorId = member.getStaff().getUserId();
        Handover handover = lockHandover(handoverId);
        TripStop stop = handover.getTripStop();
        Long tripId = stop.getVehicleTrip().getId();
        boolean receiving = handover.getHandoverType() == HandoverType.PICKUP;
        if (receiving && handover.getStatus() == HandoverStatus.DONE) {
            return crewTrip(tripId, username);
        }
        requirePending(handover);
        if (receiving && pickupAlreadyResolved(stop.getId())) {
            closeDuplicatePickups(stop.getId(), clock.instant());
            return crewTrip(tripId, username);
        }
        requireShiftReceiver(handover, member);
        var shift = handover.getHandoverType() == HandoverType.SHIFT ? custody.shiftAtStop(stop, actorId) : null;
        if (shift != null && shift.alreadyResponsible()) {
            handover.setStatus(HandoverStatus.FAILED);
            handover.setHandedAt(clock.instant());
            return crewTrip(tripId, username);
        }
        // Con đã bị từ chối nhận (chặng đã hủy) không còn trong lần giao nhận
        List<HandoverHorse> results = scans.findById_HandoverId(handover.getId()).stream()
                .filter(r -> r.getHorseLeg().getStatus() != HorseLegStatus.CANCELLED).toList();
        if (shift != null || TripExecutionPolicy.isStableHandover(handover.getHandoverType()) || isAlighting(handover)) {
            List<HorseLeg> eligibleLegs = shift == null ? candidates(handover) : shift.eligible();
            Set<Long> eligible = eligibleLegs.stream().map(HorseLeg::getId).collect(Collectors.toSet());
            results = results.stream().filter(r -> eligible.contains(r.getId().getHorseLegId())).toList();
        }
        TripExecutionPolicy.requireSomeMatched(results.stream().map(HandoverHorse::getMicrochipMatched).toList());
        if (receiving) {
            var matchedIds = results.stream().filter(r -> Boolean.TRUE.equals(r.getMicrochipMatched()))
                    .map(r -> r.getId().getHorseLegId()).collect(Collectors.toSet());
            if (candidates(handover).stream().anyMatch(leg -> !matchedIds.contains(leg.getId()))) {
                throw new DispatchRuleException("horseLegId", "Còn ngựa chưa nhận hoặc chưa ghi đón hụt.", true);
            }
        }
        // Nhận và giao ngựa phải có biên bản đã ký (MAN_HINH 4.3, 6.5)
        if (needsRecord(handover) && !evidence.hasRecord(handover.getId())) {
            throw new DispatchRuleException("record", "Chưa có biên bản giao nhận. Chụp biên bản đã ký trước khi hoàn tất.", true);
        }
        List<HandoverHorse> matched = results.stream().filter(r -> Boolean.TRUE.equals(r.getMicrochipMatched())).toList();
        List<HorseLeg> matchedLegs = matched.stream().map(HandoverHorse::getHorseLeg).toList();
        // Khóa đơn trước khi đọc giấy tờ để kiểm trên kết quả duyệt mới nhất.
        outcomes.lockBookingsForCompletion(handover.getHandoverType(), matchedLegs);
        if (receiving || handover.getHandoverType() == HandoverType.DELIVERY) {
            matched.forEach(evidence::requireHorseEvidence);
        }
        if (needsRecord(handover)) {
            Set<Long> bookingIds = matched.stream().map(row -> row.getHorseLeg().getBookingHorse().getBooking().getId())
                    .collect(Collectors.toSet());
            evidence.requirePapers(handover.getId(), bookingIds, receiving);
        }
        if (handover.getHandoverType() == HandoverType.DELIVERY) {
            Map<String, String> recipients = new LinkedHashMap<>();
            for (HandoverHorse row : matched) {
                var booking = row.getHorseLeg().getBookingHorse().getBooking();
                row.setRecipientName(recipients.computeIfAbsent(booking.getCode(),
                        code -> evidence.requireRecipient(booking, request == null ? null : request.forBooking(code))));
            }
        }
        custody.finishStablePlan(handover, matchedLegs);
        Instant now = clock.instant();
        outcomes.complete(handover, matchedLegs, now, actorId);
        if (receiving && pickupAlreadyResolved(stop.getId())) {
            closeDuplicatePickups(stop.getId(), now);
        }
        return crewTrip(tripId, username);
    }

    private boolean pickupAlreadyResolved(Long stopId) {
        boolean hasCompleted = handovers.existsByTripStop_IdAndStatusAndHandoverTypeIn(stopId, HandoverStatus.DONE,
                Set.of(HandoverType.PICKUP));
        boolean hasPlannedBoarding = !legs.findByBoardTripStop_IdAndStatusIn(stopId, Set.of(HorseLegStatus.PLANNED)).isEmpty();
        return TripExecutionPolicy.pickupAlreadyResolved(hasCompleted, hasPlannedBoarding);
    }

    private void closeDuplicatePickups(Long stopId, Instant at) {
        for (Handover draft : handovers.findByTripStop_IdAndHandoverTypeAndStatusOrderById(stopId, HandoverType.PICKUP, HandoverStatus.PENDING)) {
            Handover locked = lockHandover(draft.getId());
            if (locked.getStatus() == HandoverStatus.PENDING) {
                locked.setStatus(HandoverStatus.FAILED);
                locked.setHandedAt(at);
            }
        }
    }

    @Override
    @Transactional
    public CrewTripResponseDto refuse(Long handoverId, HandoverRefusalRequestDto request, MultipartFile photo, String username) {
        Handover handover = pending(handoverId, username);
        Long tripId = handover.getTripStop().getVehicleTrip().getId();
        Long actorId = custody.requireCrew(tripId, username).getStaff().getUserId();
        if (handover.getHandoverType() != HandoverType.PICKUP) {
            throw new DispatchRuleException("handoverType", "Chỉ từ chối ngựa tại điểm đón.", true);
        }
        HorseLeg leg = candidates(handover).stream().filter(l -> l.getId().equals(request.horseLegId())).findFirst()
                .orElseThrow(() -> new DispatchRuleException("horseLegId", "Con ngựa này không thuộc lần giao nhận.", true));
        outcomes.refuseAtStop(leg.getBookingHorse().getId(), request, photo,
                handover.getTripStop().getActualArrivalAt(), clock.instant(), actorId);
        // Không còn con nào để nhận thì lần giao nhận kết thúc là không thành, xe được rời điểm dừng
        if (candidates(handover).isEmpty()) {
            handover.setStatus(HandoverStatus.FAILED);
            handover.setHandedAt(clock.instant());
            handovers.flush();
        }
        return crewTrip(tripId, username);
    }

    // Quá hạn trả số dư (#32): trả mọi con đã nhận lên xe ở nơi nhận này (điểm đón) trong một thao tác.
    // Dùng lại mã chip đã khớp và cách nhập lúc nhận ngựa
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
            scans.findFirstByHorseLeg_IdAndHandover_HandoverTypeAndMicrochipMatchedTrue(leg.getId(), HandoverType.PICKUP)
                    .ifPresent(pickup -> {
                        returned.setScannedMicrochip(pickup.getScannedMicrochip());
                        returned.setMicrochipEntryMethod(pickup.getMicrochipEntryMethod());
                        returned.setManualEntryReason(pickup.getManualEntryReason());
                    });
            returned.setMicrochipMatched(true);
            scans.save(returned);
        }
        scans.flush();
        evidence.returnOriginals(handover, boarded.stream().map(leg -> leg.getBookingHorse().getBooking().getId())
                .collect(Collectors.toSet()));
    }

    // Con cần giao nhận.
    private List<HorseLeg> candidates(Handover handover) {
        Long stopId = handover.getTripStop().getId();
        if (TripExecutionPolicy.isStableHandover(handover.getHandoverType())) {
            return custody.stableCandidates(handover);
        }
        if (handover.getHandoverType() == HandoverType.SHIFT) {
            return custody.shiftAtStop(handover.getTripStop(), handover.getHandledBy().getUserId()).eligible();
        }
        boolean boarding = TripExecutionPolicy.isBoarding(handover.getHandoverType());
        List<HorseLeg> found = boarding
                ? legs.findByBoardTripStop_IdAndStatusIn(stopId, Set.of(HorseLegStatus.PLANNED, HorseLegStatus.IN_PROGRESS))
                : legs.findByAlightTripStop_IdAndStatusIn(stopId, Set.of(HorseLegStatus.IN_PROGRESS, HorseLegStatus.COMPLETED));
        if (isAlighting(handover)) {
            Set<Long> atStable = custody.legsAtStable(handover.getTripStop());
            found = found.stream().filter(l -> !atStable.contains(l.getId())).toList();
        }
        if (handover.getHandoverType() == HandoverType.HUB_IN) return withFailedAwaitingHub(handover, found);
        return found;
    }

    // Giao thất bại (B5): con không giao được ở điểm trước của lượt này còn trên xe, giao vào hub ở điểm hub sau đó
    private List<HorseLeg> withFailedAwaitingHub(Handover handover, List<HorseLeg> alighting) {
        TripStop stop = handover.getTripStop();
        Long self = handover.getId() == null ? -1L : handover.getId();
        Set<Long> atStable = custody.legsAtStable(stop);
        List<HorseLeg> failed = legs.findActiveOnTrip(stop.getVehicleTrip().getId(), Set.of(HorseLegStatus.CANCELLED)).stream()
                .filter(l -> l.getStatus() == HorseLegStatus.FAILED && l.getAlightTripStop().getSequenceNumber() < stop.getSequenceNumber())
                .filter(l -> !atStable.contains(l.getId()))
                .filter(l -> !scans.existsByHorseLeg_IdAndHandover_HandoverTypeAndHandover_StatusAndHandover_IdNot(
                        l.getId(), HandoverType.HUB_IN, HandoverStatus.DONE, self))
                .toList();
        return failed.isEmpty() ? alighting : java.util.stream.Stream.concat(alighting.stream(), failed.stream()).toList();
    }

    private HandoverResponseDto describe(Handover handover) {
        Map<Long, HandoverHorse> byLeg = scans.findById_HandoverId(handover.getId()).stream()
                .collect(Collectors.toMap(s -> s.getId().getHorseLegId(), s -> s));
        // Lần trả ngựa đã xong: các con là các con đã ghi trong lần đó (chặng đã hủy)
        List<HorseLeg> horses = handover.getHandoverType() == HandoverType.RETURN
                || (handover.getHandoverType() == HandoverType.SHIFT || TripExecutionPolicy.isStableHandover(handover.getHandoverType()))
                        && handover.getStatus() != HandoverStatus.PENDING
                ? byLeg.values().stream().map(HandoverHorse::getHorseLeg).toList() : candidates(handover);
        return HandoverResponseDto.from(handover, horses, byLeg, evidence.hasRecord(handover.getId()),
                evidence.photographedLegs(handover.getId()));
    }

    @Override
    @Transactional
    public Handover pendingForCrew(Long handoverId, String username) {
        return pending(handoverId, username);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> bookingIdsOf(Handover handover) {
        return candidates(handover).stream().map(l -> l.getBookingHorse().getBooking().getId()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void closeEmptyAlightingHandovers(List<HorseLeg> failed, Instant at) {
        List<Long> stopIds = failed.stream().map(leg -> leg.getAlightTripStop().getId()).distinct().toList();
        for (Handover handover : handovers.findByTripStop_IdInOrderById(stopIds)) {
            if (handover.getStatus() == HandoverStatus.PENDING && isAlighting(handover)
                    && candidates(handover).stream().allMatch(leg -> leg.getStatus() == HorseLegStatus.COMPLETED)) {
                handover.setStatus(HandoverStatus.FAILED);
                handover.setHandedAt(at);
            }
        }
    }

    private static boolean isAlighting(Handover handover) {
        HandoverType type = handover.getHandoverType();
        return type == HandoverType.HUB_IN || type == HandoverType.DELIVERY;
    }

    private static boolean needsRecord(Handover handover) {
        return handover.getHandoverType() == HandoverType.PICKUP || handover.getHandoverType() == HandoverType.DELIVERY;
    }

    private Handover pending(Long handoverId, String username) {
        Handover handover = lockHandover(handoverId);
        CrewAssignment member = custody.requireCrew(handover.getTripStop().getVehicleTrip().getId(), username);
        requireShiftReceiver(handover, member);
        requirePending(handover);
        return handover;
    }

    private static void requireShiftReceiver(Handover handover, CrewAssignment member) {
        if (handover.getHandoverType() != HandoverType.SHIFT) return;
        requireShiftEscort(member);
        if (!Objects.equals(handover.getHandledBy().getUserId(), member.getStaff().getUserId())) {
            throw new AccessDeniedException("Chỉ hộ tống nhận ca mới được xử lý phiếu bàn giao này.");
        }
    }

    private static void requireShiftEscort(CrewAssignment member) {
        if (member.getCrewRole() != CrewRole.ESCORT) {
            throw new AccessDeniedException("Chỉ hộ tống nhận bàn giao ca.");
        }
    }

    private Handover lockHandover(Long id) {
        return handovers.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lần giao nhận " + id));
    }

    private static void requirePending(Handover handover) {
        if (handover.getStatus() != HandoverStatus.PENDING) {
            throw new DispatchRuleException("status", "Lần giao nhận đã kết thúc.", true);
        }
        if (handover.getTripStop().getVehicleTrip().getStatus() != VehicleTripStatus.IN_PROGRESS) {
            throw new DispatchRuleException("status", "Lượt xe chưa xuất phát hoặc đã kết thúc.", true);
        }
    }
}
