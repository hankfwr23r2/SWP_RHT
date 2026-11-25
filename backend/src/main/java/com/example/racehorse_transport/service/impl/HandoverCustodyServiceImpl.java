package com.example.racehorse_transport.service.impl;

import static com.example.racehorse_transport.entity.enums.HandoverType.STAY_IN;
import static com.example.racehorse_transport.entity.enums.HandoverType.STAY_OUT;

import com.example.racehorse_transport.entity.CrewAssignment;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.IncidentPlan;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.IncidentPlanStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.IncidentPolicy;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
import com.example.racehorse_transport.repository.CrewAssignmentRepository;
import com.example.racehorse_transport.repository.HandoverHorseRepository;
import com.example.racehorse_transport.repository.HandoverRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.HandoverCustodyService;
import com.example.racehorse_transport.service.HorseStayService;
import com.example.racehorse_transport.service.IncidentPlanService;
import java.util.LinkedHashMap;
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
public class HandoverCustodyServiceImpl implements HandoverCustodyService {

    private static final Set<HandoverType> STABLE_TYPES = Set.of(STAY_IN, STAY_OUT);

    private final HandoverRepository handovers;
    private final HandoverHorseRepository scans;
    private final HorseLegRepository legs;
    private final TripStopRepository stops;
    private final VehicleTripRepository trips;
    private final HorseStayService stays;
    private final IncidentPlanService plans;
    private final CrewAssignmentRepository crew;

    @Override
    @Transactional(readOnly = true)
    public CrewAssignment requireCrew(Long tripId, String username) {
        return crew.findFirstByVehicleTrip_IdAndStaff_User_UsernameAndStatusNot(tripId, username, CrewAssignmentStatus.RELEASED)
                .orElseThrow(() -> new AccessDeniedException("Bạn không ở trong tổ lái của lượt xe này."));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockCurrentStop(TripStop stop) {
        lockRunningTrip(stop.getVehicleTrip().getId());
        if (!stops.existsByIdAndVehicleTrip_StatusAndActualArrivalAtIsNotNullAndActualDepartureAtIsNull(
                stop.getId(), VehicleTripStatus.IN_PROGRESS)) {
            throw new DispatchRuleException("tripStopId", "Chỉ giao nhận khi xe đang ở điểm dừng này.", true);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CrewAssignment lockForCompletion(Long handoverId, String username) {
        var context = handovers.findCompletionContext(handoverId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lần giao nhận " + handoverId));
        CrewAssignment member = requireCrew(context.getTripId(), username);
        // Cùng thứ tự khóa với thao tác lập phương án mới và hủy chặng.
        if (context.getPlanId() != null) {
            plans.lockApprovedStablePlan(context.getPlanId());
        }
        lockRunningTrip(context.getTripId());
        return member;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void prepareStableHandover(Handover handover, Long partyId, Long planId) {
        TripStop stop = handover.getTripStop();
        if (handover.getHandoverType() == STAY_OUT) {
            lockCurrentStop(stop);
            TripExecutionPolicy.requireNetworkStableStop(stop.getLocation() != null);
            handover.setFromParty(partnerToCollectFrom(stop, partyId));
        } else {
            Long selected = planId == null ? findApplicablePlan(stop, partyId) : planId;
            IncidentPlan plan = selected == null ? null : plans.lockApprovedStablePlan(selected);
            lockCurrentStop(stop);
            if (plan == null) {
                TripExecutionPolicy.requireHandoverFitsStop(STAY_IN, stop.getStopType());
                if (findApplicablePlan(stop, partyId) != null) {
                    throw new DispatchRuleException("incidentPlanId", "Phương án gửi chuồng vừa thay đổi. Hãy mở lại lần giao nhận.", true);
                }
            } else {
                TripExecutionPolicy.requireNetworkStableStop(stop.getLocation() != null);
                if (!IncidentPolicy.requireTrip(plan.getIncident()).getId().equals(stop.getVehicleTrip().getId())) {
                    throw new DispatchRuleException("incidentPlanId", "Phương án gửi chuồng không thuộc lượt xe này.", true);
                }
                if (plan.getHandlerParty() == null || !plan.getHandlerParty().getId().equals(partyId)) {
                    throw new DispatchRuleException("partyId", "Chỉ gửi cho đối tác chuồng của phương án đã duyệt.", true);
                }
                handover.setIncidentPlan(plan);
            }
            if (partyId == null) {
                throw new DispatchRuleException("partyId", "Chọn chuồng gửi qua đêm.");
            }
            handover.setToParty(stays.requireStablePartner(partyId));
        }
        if (handovers.existsByTripStop_IdAndStatusAndHandoverTypeIn(stop.getId(), HandoverStatus.PENDING, STABLE_TYPES)) {
            throw new DispatchRuleException("handoverType", "Còn lần giao nhận với chuồng chưa hoàn tất tại điểm dừng này.", true);
        }
    }

    private Long findApplicablePlan(TripStop stop, Long partyId) {
        List<IncidentPlan> approved = plans.approvedStablePlans(stop.getVehicleTrip().getId());
        if (approved.isEmpty()) return null;
        var present = onBoard(stop, true);
        List<IncidentPlan> applicable = approved.stream()
                .filter(plan -> present.stream().anyMatch(leg -> covers(plan, leg)))
                .toList();
        if (applicable.isEmpty()) return null;
        TripExecutionPolicy.requireNetworkStableStop(stop.getLocation() != null);
        List<IncidentPlan> matching = applicable.stream()
                .filter(plan -> plan.getHandlerParty() != null && plan.getHandlerParty().getId().equals(partyId))
                .toList();
        if (matching.isEmpty()) {
            throw new DispatchRuleException("partyId", "Chỉ gửi cho đối tác chuồng của phương án đã duyệt.", true);
        }
        if (matching.size() > 1) {
            throw new DispatchRuleException("incidentPlanId", "Chọn phương án sự cố cần thực hiện.", true);
        }
        return matching.getFirst().getId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<HorseLeg> stableCandidates(Handover handover) {
        TripStop stop = handover.getTripStop();
        if (handover.getHandoverType() == STAY_OUT) {
            return horsesAtStable(stop).values().stream()
                    .filter(row -> row.getHandover().getToParty().getId().equals(handover.getFromParty().getId()))
                    .map(HandoverHorse::getHorseLeg).toList();
        }
        IncidentPlan plan = handover.getIncidentPlan();
        return onBoard(stop, plan != null).stream().filter(leg -> plan == null || covers(plan, leg)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> legsAtStable(TripStop stop) {
        return horsesAtStable(stop).keySet();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void finishStablePlan(Handover handover, List<HorseLeg> matched) {
        IncidentPlan plan = handover.getIncidentPlan();
        if (plan == null) return;
        Set<Long> matchedIds = matched.stream().map(HorseLeg::getId).collect(Collectors.toSet());
        if (stableCandidates(handover).stream().anyMatch(leg -> !matchedIds.contains(leg.getId()))) {
            throw new DispatchRuleException("horses", "Quét đúng chip của mọi con thuộc phương án trước khi gửi chuồng.", true);
        }
        plan.setStatus(IncidentPlanStatus.DONE);
    }

    private Party partnerToCollectFrom(TripStop stop, Long partyId) {
        return horsesAtStable(stop).values().stream().map(row -> row.getHandover().getToParty())
                .filter(party -> partyId == null || party.getId().equals(partyId)).reduce((first, last) -> last)
                .orElseThrow(() -> new DispatchRuleException("handoverType", "Không có con ngựa nào đang gửi chuồng ở điểm dừng này.", true));
    }

    private Map<Long, HandoverHorse> horsesAtStable(TripStop stop) {
        Map<Long, HandoverHorse> atStable = new LinkedHashMap<>();
        for (var row : scans.findMatchedAtStop(stop.getId(), HandoverStatus.DONE, STABLE_TYPES)) {
            Long legId = row.getHorseLeg().getId();
            if (row.getHandover().getHandoverType() == STAY_IN) {
                atStable.put(legId, row);
            } else {
                atStable.remove(legId);
            }
        }
        return atStable;
    }

    private List<HorseLeg> onBoard(TripStop stop, boolean includeEndpoints) {
        int at = stop.getSequenceNumber();
        Set<HorseLegStatus> statuses = includeEndpoints ? Set.of(HorseLegStatus.IN_PROGRESS, HorseLegStatus.FAILED)
                : Set.of(HorseLegStatus.IN_PROGRESS);
        return legs.findOnBoardForIncident(stop.getVehicleTrip().getId(), statuses).stream()
                .filter(leg -> {
                    int board = leg.getBoardTripStop().getSequenceNumber();
                    int alight = leg.getAlightTripStop().getSequenceNumber();
                    if (leg.getStatus() == HorseLegStatus.FAILED) return alight <= at;
                    return includeEndpoints ? board <= at && at <= alight : board < at && at < alight;
                }).toList();
    }

    private static boolean covers(IncidentPlan plan, HorseLeg leg) {
        HorseLeg affected = plan.getIncident().getHorseLeg();
        return affected == null || Objects.equals(affected.getId(), leg.getId());
    }

    private void lockRunningTrip(Long tripId) {
        var trip = trips.findForUpdate(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lượt xe " + tripId));
        TripExecutionPolicy.requireInProgress(trip.getStatus());
    }
}
