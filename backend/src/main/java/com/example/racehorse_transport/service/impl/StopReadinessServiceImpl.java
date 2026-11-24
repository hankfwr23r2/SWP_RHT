package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.HorseHealthLog;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
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
import com.example.racehorse_transport.service.StopReadinessService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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

    @Override
    @Transactional
    public void requireCanLeave(TripStop stop, String earlyReason, Long actorUserId) {
        TripExecutionPolicy.requireCanDepart(stop.getActualArrivalAt() != null, stop.getActualDepartureAt() != null,
                handovers.countByTripStop_IdAndStatus(stop.getId(), HandoverStatus.PENDING));
        requireNoHorseLeftAtStable(stop);
        // Nơi nhận ngựa: điểm đón, cửa khẩu với đơn nhập (nhận từ đại lý), hub với khách tự chở tới (SELF)
        if (stop.getStopType() == TripStopType.PICKUP || stop.getStopType() == TripStopType.BORDER || stop.getStopType() == TripStopType.HUB) {
            requirePaid(stop);
        } else if (REST_STOPS.contains(stop.getStopType())) {
            requireRestStopDone(stop, earlyReason, actorUserId);
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
        Map<Long, String> boarded = new LinkedHashMap<>();
        for (HorseLeg leg : legs.findByBoardTripStop_IdAndStatusIn(stop.getId(), Set.of(HorseLegStatus.IN_PROGRESS))) {
            // Đơn đã IN_OPERATION là đã trả đủ khi rời nơi nhận trước đó
            if (leg.getBookingHorse().getBooking().getStatus() != BookingStatus.CONFIRMED) continue;
            boarded.put(leg.getBookingHorse().getBooking().getId(), leg.getBookingHorse().getBooking().getCode());
        }
        if (boarded.isEmpty()) return;
        boarded.forEach((id, code) -> {
            if (!payments.isPaidInFull(id)) {
                throw new DispatchRuleException("payment", "Đơn " + code + " chưa trả đủ. Xe chưa rời nơi nhận được.", true);
            }
        });
    }

    // Quy tắc #34: mọi con đang trên xe đã ghi uống và ăn ở trạm này, và đã nghỉ đủ; rời sớm thì ghi lý do vào nhật ký
    private void requireRestStopDone(TripStop stop, String earlyReason, Long actorUserId) {
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
        int minRest = configs.getInt("MIN_REST_MINUTES");
        HealthLogPolicy.requireRested(stop.getActualArrivalAt(), now, minRest, earlyReason);
        if (now.isBefore(stop.getActualArrivalAt().plusSeconds(60L * minRest))) {
            audit.record(actorUserId, "LEAVE_REST_STOP_EARLY", "trip_stop", stop.getId(),
                    Map.of("arrivedAt", stop.getActualArrivalAt().toString()), Map.of("leftAt", now.toString(), "reason", earlyReason.strip()));
        }
    }
}
