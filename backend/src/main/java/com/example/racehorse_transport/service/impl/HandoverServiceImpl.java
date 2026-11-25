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
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.CrewRole;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.DocumentRepository;
import com.example.racehorse_transport.repository.HandoverHorseRepository;
import com.example.racehorse_transport.repository.HandoverRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.service.DispatchService;
import com.example.racehorse_transport.service.HandoverOutcomeService;
import com.example.racehorse_transport.service.HandoverService;
import com.example.racehorse_transport.service.HorseLegService;
import java.time.Clock;
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
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HandoverServiceImpl implements HandoverService {

    static final String RECORD_TYPE = "HANDOVER_RECORD";

    private final HandoverRepository handovers;
    private final HandoverHorseRepository scans;
    private final HorseLegRepository legs;
    private final TripStopRepository stops;
    private final CrewAssignmentRepository crew;
    private final DispatchService dispatch;
    private final HorseLegService horseLegs;
    private final HandoverOutcomeService outcomes;
    private final DocumentRepository documents;
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
        if (request.handoverType() == HandoverType.AGENT) {
            assignAgent(handover, request.partyId());
        }
        // Nghỉ đêm: gửi đối tác chuồng (bắt buộc chọn), nhận lại từ chính đối tác đã nhận
        if (request.handoverType() == HandoverType.STAY_IN) {
            if (request.partyId() == null) {
                throw new DispatchRuleException("partyId", "Chọn chuồng gửi qua đêm.");
            }
            handover.setToParty(outcomes.stablePartner(request.partyId()));
        }
        if (request.handoverType() == HandoverType.STAY_OUT) {
            handover.setFromParty(overnightPartner(stop));
        }
        // Người nhận ca là hộ tống đang trong tổ lái (quy tắc #38)
        if (request.handoverType() == HandoverType.SHIFT && member.getCrewRole() != CrewRole.ESCORT) {
            throw new DispatchRuleException("handoverType", "Chỉ hộ tống nhận bàn giao ca.", true);
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
        // Nhận và giao ngựa phải có biên bản đã ký (MAN_HINH 4.3, 6.5)
        if (needsRecord(handover) && !documents.existsByHandover_IdAndDocumentType_Code(handover.getId(), RECORD_TYPE)) {
            throw new DispatchRuleException("record", "Chưa có biên bản giao nhận. Chụp biên bản đã ký trước khi hoàn tất.", true);
        }
        outcomes.complete(handover, results.stream().filter(r -> Boolean.TRUE.equals(r.getMicrochipMatched()))
                .map(HandoverHorse::getHorseLeg).toList(), clock.instant(), actorId);
        return crewTrip(tripId, username);
    }

    @Override
    @Transactional
    public CrewTripResponseDto refuse(Long handoverId, HandoverRefusalRequestDto request, String username) {
        Handover handover = pending(handoverId, username);
        Long tripId = handover.getTripStop().getVehicleTrip().getId();
        Long actorId = requireCrew(tripId, username).getStaff().getUserId();
        // Nơi nhận: điểm đón của khách, hoặc cửa khẩu nhận từ đại lý (đơn nhập)
        boolean fromAgent = handover.getHandoverType() == HandoverType.AGENT && handover.getFromParty() != null;
        if (handover.getHandoverType() != HandoverType.PICKUP && !fromAgent) {
            throw new DispatchRuleException("handoverType", "Chỉ từ chối nhận ngựa khi nhận ngựa từ khách hoặc từ đại lý ở cửa khẩu.", true);
        }
        HorseLeg leg = candidates(handover).stream().filter(l -> l.getId().equals(request.horseLegId())).findFirst()
                .orElseThrow(() -> new DispatchRuleException("horseLegId", "Con ngựa này không thuộc lần giao nhận.", true));
        outcomes.refuse(leg.getBookingHorse().getId(), request.reason().strip(), clock.instant(), actorId);
        // Không còn con nào để nhận thì lần giao nhận kết thúc là không thành, xe được rời điểm dừng
        if (candidates(handover).isEmpty()) {
            handover.setStatus(HandoverStatus.FAILED);
            handover.setHandedAt(clock.instant());
            handovers.flush();
        }
        return crewTrip(tripId, username);
    }

    // Quá hạn trả số dư (#32): trả mọi con đã nhận lên xe ở nơi nhận này (điểm đón) trong một thao tác.
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
            returned.setScannedMicrochip(scans.findFirstByHorseLeg_IdAndHandover_HandoverTypeAndMicrochipMatchedTrue(leg.getId(),
                            HandoverType.PICKUP)
                    .map(HandoverHorse::getScannedMicrochip).orElse(null));
            returned.setMicrochipMatched(true);
            scans.save(returned);
        }
        scans.flush();
    }

    // Bàn giao ở cửa khẩu (#28): đại lý của chặng kế tiếp (đơn xuất, công ty giao) hoặc chặng trước (đơn nhập, đại lý giao)
    private void assignAgent(Handover handover, Long partyId) {
        Long stopId = handover.getTripStop().getId();
        Map<Long, Party> outgoing = new LinkedHashMap<>();
        Map<Long, Party> incoming = new LinkedHashMap<>();
        legs.findByAlightTripStop_IdAndStatusIn(stopId, Set.of(HorseLegStatus.IN_PROGRESS)).stream()
                .map(l -> adjacentAgent(l, true)).filter(Objects::nonNull).forEach(p -> outgoing.put(p.getId(), p));
        legs.findByBoardTripStop_IdAndStatusIn(stopId, Set.of(HorseLegStatus.PLANNED)).stream()
                .map(l -> adjacentAgent(l, false)).filter(Objects::nonNull).forEach(p -> incoming.put(p.getId(), p));
        Set<Long> agents = new LinkedHashSet<>(outgoing.keySet());
        agents.addAll(incoming.keySet());
        if (partyId != null) agents.retainAll(Set.of(partyId));
        if (agents.size() > 1) {
            throw new DispatchRuleException("partyId", "Có nhiều đại lý ở cửa khẩu này. Chọn đại lý cần bàn giao.", true);
        }
        if (agents.isEmpty()) return;
        Long agent = agents.iterator().next();
        if (outgoing.containsKey(agent)) {
            handover.setToParty(outgoing.get(agent));
        } else {
            handover.setFromParty(incoming.get(agent));
        }
    }

    // Đại lý chở chặng ngay sau (next) hoặc ngay trước chặng này của con ngựa
    private Party adjacentAgent(HorseLeg leg, boolean next) {
        List<HorseLeg> chain = legs.findByBookingHorse_IdOrderBySequenceNumber(leg.getBookingHorse().getId()).stream()
                .filter(l -> l.getStatus() != HorseLegStatus.CANCELLED && l.getStatus() != HorseLegStatus.FAILED).toList();
        int at = chain.indexOf(leg);
        int other = next ? at + 1 : at - 1;
        return at < 0 || other < 0 || other >= chain.size() ? null : chain.get(other).getAgentParty();
    }

    // Con cần giao nhận: lên xe ở điểm này (đón, ra hub, nhận từ đại lý) hoặc xuống xe ở điểm này (giao, vào hub, giao cho đại lý)
    private List<HorseLeg> candidates(Handover handover) {
        Long stopId = handover.getTripStop().getId();
        if (handover.getHandoverType() == HandoverType.STAY_IN || handover.getHandoverType() == HandoverType.STAY_OUT) {
            // Các con đi qua điểm này: STAY_IN là con còn trên xe, STAY_OUT là con đang gửi chuồng (lần giao nhận cuối ở đây là STAY_IN)
            TripStop stop = handover.getTripStop();
            int at = stop.getSequenceNumber();
            Map<Long, HandoverType> last = lastHandoverAt(stop);
            boolean in = handover.getHandoverType() == HandoverType.STAY_IN;
            return legs.findActiveOnTrip(stop.getVehicleTrip().getId(), Set.of(HorseLegStatus.CANCELLED, HorseLegStatus.FAILED)).stream()
                    .filter(l -> l.getStatus() == HorseLegStatus.IN_PROGRESS
                            && l.getBoardTripStop().getSequenceNumber() < at && at < l.getAlightTripStop().getSequenceNumber())
                    .filter(l -> in != (last.get(l.getId()) == HandoverType.STAY_IN))
                    .toList();
        }
        if (handover.getHandoverType() == HandoverType.SHIFT) {
            // Bàn giao ca: các con đang trên xe ở điểm này (đã lên, chưa tới điểm xuống)
            TripStop stop = handover.getTripStop();
            int at = stop.getSequenceNumber();
            return legs.findActiveOnTrip(stop.getVehicleTrip().getId(), Set.of(HorseLegStatus.CANCELLED, HorseLegStatus.FAILED)).stream()
                    .filter(l -> l.getStatus() == HorseLegStatus.IN_PROGRESS
                            && l.getBoardTripStop().getSequenceNumber() <= at && at < l.getAlightTripStop().getSequenceNumber())
                    .toList();
        }
        boolean boarding = TripExecutionPolicy.isBoarding(handover.getHandoverType(), handover.getFromParty() != null);
        List<HorseLeg> found = boarding
                ? legs.findByBoardTripStop_IdAndStatusIn(stopId, Set.of(HorseLegStatus.PLANNED, HorseLegStatus.IN_PROGRESS))
                : legs.findByAlightTripStop_IdAndStatusIn(stopId, Set.of(HorseLegStatus.IN_PROGRESS, HorseLegStatus.COMPLETED));
        if (handover.getHandoverType() == HandoverType.HUB_IN) return withFailedAwaitingHub(handover, found);
        if (handover.getHandoverType() != HandoverType.AGENT) return found;
        Party agent = boarding ? handover.getFromParty() : handover.getToParty();
        if (agent == null) return List.of();
        return found.stream().filter(l -> {
            Party adjacent = adjacentAgent(l, !boarding);
            return adjacent != null && adjacent.getId().equals(agent.getId());
        }).toList();
    }

    // Giao thất bại (B5): con không giao được ở điểm trước của lượt này còn trên xe, giao vào hub ở điểm hub sau đó
    private List<HorseLeg> withFailedAwaitingHub(Handover handover, List<HorseLeg> alighting) {
        TripStop stop = handover.getTripStop();
        Long self = handover.getId() == null ? -1L : handover.getId();
        List<HorseLeg> failed = legs.findActiveOnTrip(stop.getVehicleTrip().getId(), Set.of(HorseLegStatus.CANCELLED)).stream()
                .filter(l -> l.getStatus() == HorseLegStatus.FAILED && l.getAlightTripStop().getSequenceNumber() < stop.getSequenceNumber())
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
                ? byLeg.values().stream().map(HandoverHorse::getHorseLeg).toList() : candidates(handover);
        return HandoverResponseDto.from(handover, horses, byLeg, documents.existsByHandover_IdAndDocumentType_Code(handover.getId(), RECORD_TYPE));
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

    // Loại giao nhận DONE cuối cùng (chip khớp) của từng chặng tại điểm dừng
    private Map<Long, HandoverType> lastHandoverAt(TripStop stop) {
        Map<Long, HandoverType> last = new LinkedHashMap<>();
        handovers.findByTripStop_IdInOrderById(List.of(stop.getId())).stream().filter(h -> h.getStatus() == HandoverStatus.DONE)
                .forEach(h -> scans.findById_HandoverId(h.getId()).stream().filter(r -> Boolean.TRUE.equals(r.getMicrochipMatched()))
                        .forEach(r -> last.put(r.getId().getHorseLegId(), h.getHandoverType())));
        return last;
    }

    // Đối tác đã nhận ngựa nghỉ đêm ở điểm này (lần STAY_IN mới nhất)
    private Party overnightPartner(TripStop stop) {
        return handovers.findByTripStop_IdInOrderById(List.of(stop.getId())).stream()
                .filter(h -> h.getStatus() == HandoverStatus.DONE && h.getHandoverType() == HandoverType.STAY_IN)
                .reduce((a, b) -> b).map(Handover::getToParty)
                .orElseThrow(() -> new DispatchRuleException("handoverType", "Không có con ngựa nào đang gửi chuồng ở điểm dừng này.", true));
    }

    private static boolean needsRecord(Handover handover) {
        return handover.getHandoverType() == HandoverType.PICKUP || handover.getHandoverType() == HandoverType.DELIVERY;
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
