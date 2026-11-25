package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.HorseHealthLog;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.PickupMissChoice;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.policy.HealthLogPolicy;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
import com.example.racehorse_transport.repository.HandoverHorseRepository;
import com.example.racehorse_transport.repository.HandoverRepository;
import com.example.racehorse_transport.repository.HorseHealthLogRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.PaymentReadinessService;
import com.example.racehorse_transport.service.PickupBalanceService;
import com.example.racehorse_transport.service.StopReadinessService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StopReadinessServiceImpl implements StopReadinessService {

    private static final Set<TripStopType> REST_STOPS = Set.of(TripStopType.REST, TripStopType.VITAL_CHECK);

    private final HandoverRepository handovers;
    private final HandoverHorseRepository scans;
    private final HorseLegRepository legs;
    private final HorseHealthLogRepository healthLogs;
    private final PaymentReadinessService payments;
    private final SystemConfigService configs;
    private final AuditService audit;
    private final Clock clock;
    private final PickupBalanceService pickupBalances;
    private final com.example.racehorse_transport.service.StablingTimelineService stablingTimeline;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isPaidInFullForDeparture(Long bookingId) {
        payments.lockBooking(bookingId);
        return payments.isPaidInFull(bookingId);
    }

    @Override
    @Transactional
    public boolean requireCanLeave(TripStop stop, String earlyReason, Long actorUserId) {
        closeEmptyHubOut(stop);
        TripExecutionPolicy.requireCanDepart(stop.getActualArrivalAt() != null, stop.getActualDepartureAt() != null,
                handovers.countByTripStop_IdAndStatus(stop.getId(), HandoverStatus.PENDING));
        if ((stop.getStopType() == TripStopType.PICKUP || stop.getStopType() == TripStopType.BORDER)
                && !legs.findByBoardTripStop_IdAndStatusIn(stop.getId(), Set.of(HorseLegStatus.PLANNED)).isEmpty()) {
            throw new DispatchRuleException("tripStopId", "Còn ngựa chưa nhận hoặc chưa ghi đón hụt.", true);
        }
        requireNoHorseLeftAtStable(stop);
        boolean earlyOvernightDeparture = false;
        if (stop.getStopType() == TripStopType.PICKUP) {
            requirePaid(stop);
        } else if (REST_STOPS.contains(stop.getStopType())) {
            earlyOvernightDeparture = requireRestStopDone(stop, earlyReason, actorUserId);
        }
        if (stop.getVehicleTrip().getTripType() == com.example.racehorse_transport.entity.enums.VehicleTripType.LINEHAUL) {
            stablingTimeline.chargePlannedStaysOnDeparture(stop.getId(), clock.instant());
        }
        return earlyOvernightDeparture;
    }

    private void closeEmptyHubOut(TripStop stop) {
        if (stop.getStopType() != TripStopType.HUB) return;
        for (var open : handovers.findByTripStop_IdAndHandoverTypeAndStatusOrderById(
                stop.getId(), HandoverType.HUB_OUT, HandoverStatus.PENDING)) {
            handovers.findForUpdate(open.getId()).filter(row -> row.getStatus() == HandoverStatus.PENDING)
                    .filter(row -> legs.findByBoardTripStop_IdAndStatusIn(stop.getId(),
                            Set.of(HorseLegStatus.PLANNED, HorseLegStatus.IN_PROGRESS)).isEmpty())
                    .ifPresent(row -> {
                        row.setStatus(HandoverStatus.FAILED);
                        row.setHandedAt(clock.instant());
                    });
        }
    }

    // Nghỉ đêm: con đã gửi chuồng ở điểm này (lần giao nhận cuối ở đây là STAY_IN) phải được nhận lại trước khi xe đi
    private void requireNoHorseLeftAtStable(TripStop stop) {
        Map<Long, String> last = new LinkedHashMap<>();
        handovers.findByTripStop_IdInOrderById(List.of(stop.getId())).stream().filter(h -> h.getStatus() == HandoverStatus.DONE)
                .forEach(h -> scans.findById_HandoverId(h.getId()).stream().filter(r -> Boolean.TRUE.equals(r.getMicrochipMatched()))
                        .forEach(r -> last.put(r.getId().getHorseLegId(), h.getHandoverType() == HandoverType.STAY_IN
                                ? r.getHorseLeg().getBookingHorse().getHorse().getName() : null)));
        List<String> atStable = last.values().stream().filter(Objects::nonNull).toList();
        if (!atStable.isEmpty()) {
            throw new DispatchRuleException("tripStopId", "Còn ngựa đang gửi chuồng qua đêm: " + String.join(", ", atStable) + ". Nhận lại trước khi rời trạm.", true);
        }
    }

    // Quy tắc #32: mọi đơn có ngựa đã nhận lên xe ở điểm đón này phải trả đủ
    private void requirePaid(TripStop stop) {
        Map<Long, HorseLeg> boarded = new TreeMap<>();
        for (HorseLeg leg : legs.findByBoardTripStop_IdAndStatusIn(stop.getId(), Set.of(HorseLegStatus.IN_PROGRESS))) {
            var horse = leg.getBookingHorse();
            if (horse.getPickupMissChoice() == PickupMissChoice.RETRY_PICKUP) {
                boarded.put(horse.getBooking().getId(), leg);
            } else if (horse.getBooking().getStatus() == BookingStatus.CONFIRMED) {
                boarded.putIfAbsent(horse.getBooking().getId(), leg);
            }
        }
        boarded.forEach((id, leg) -> {
            var horse = leg.getBookingHorse();
            if (!isPaidInFullForDeparture(id) && (horse.getPickupMissChoice() == PickupMissChoice.RETRY_PICKUP
                    || !pickupBalances.canLeaveForHub(id))) {
                throw new DispatchRuleException("payment", "Đơn " + horse.getBooking().getCode()
                        + " chưa trả đủ. Xe chưa rời nơi nhận được.", true);
            }
        });
    }

    // Quy tắc #34: mọi con đang trên xe đã ghi uống và ăn ở trạm này, và đã nghỉ đủ; rời sớm thì ghi lý do vào nhật ký
    private boolean requireRestStopDone(TripStop stop, String earlyReason, Long actorUserId) {
        int sequence = stop.getSequenceNumber();
        List<HorseLeg> onBoard = legs.findActiveOnTrip(stop.getVehicleTrip().getId(), Set.of(HorseLegStatus.CANCELLED, HorseLegStatus.FAILED)).stream()
                .filter(l -> l.getStatus() == HorseLegStatus.IN_PROGRESS
                        && l.getBoardTripStop().getSequenceNumber() < sequence && sequence < l.getAlightTripStop().getSequenceNumber())
                .toList();
        Set<Long> logged = healthLogs.findByTripStop_IdOrderByRecordedAt(stop.getId()).stream()
                .filter(log -> log.getWaterIntake() != null && log.getFeedIntake() != null)
                .map(HorseHealthLog::getBookingHorse).map(line -> line.getId()).collect(Collectors.toSet());
        HealthLogPolicy.requireAllLogged(onBoard.stream().filter(l -> !logged.contains(l.getBookingHorse().getId()))
                .map(l -> l.getBookingHorse().getHorse().getName()).toList());
        Instant now = clock.instant();
        int overnightRestHours = configs.getInt("OVERNIGHT_REST_HOURS");
        boolean overnight = HealthLogPolicy.isOvernightRest(stop.getPlannedArrivalAt(), stop.getPlannedDepartureAt(), overnightRestHours);
        boolean rested = overnight
                ? HealthLogPolicy.requireOvernightRested(stop.getActualArrivalAt(), now, overnightRestHours, earlyReason)
                : HealthLogPolicy.requireRested(stop.getActualArrivalAt(), now, configs.getInt("MIN_REST_MINUTES"), earlyReason);
        if (!rested) {
            audit.record(actorUserId, "LEAVE_REST_STOP_EARLY", "trip_stop", stop.getId(),
                    Map.of("arrivedAt", stop.getActualArrivalAt().toString()), Map.of("leftAt", now.toString(), "reason", earlyReason.strip()));
        }
        return overnight && !rested;
    }
}
