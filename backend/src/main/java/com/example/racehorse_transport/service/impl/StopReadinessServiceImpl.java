package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.HorseHealthLog;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.policy.HealthLogPolicy;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
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
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StopReadinessServiceImpl implements StopReadinessService {

    private static final Set<TripStopType> REST_STOPS = Set.of(TripStopType.REST, TripStopType.VITAL_CHECK);

    private final HandoverRepository handovers;
    private final HorseLegRepository legs;
    private final HorseHealthLogRepository healthLogs;
    // Luồng A cài đặt ở A7. Chưa có thì không cho xe rời nơi nhận ngựa (không đoán là đã trả)
    private final ObjectProvider<PaymentReadinessService> paymentReadiness;
    private final SystemConfigService configs;
    private final AuditService audit;
    private final Clock clock;

    @Override
    @Transactional
    public void requireCanLeave(TripStop stop, String earlyReason, Long actorUserId) {
        TripExecutionPolicy.requireCanDepart(stop.getActualArrivalAt() != null, stop.getActualDepartureAt() != null,
                handovers.countByTripStop_IdAndStatus(stop.getId(), HandoverStatus.PENDING));
        if (stop.getStopType() == TripStopType.PICKUP) {
            requirePaid(stop);
        } else if (REST_STOPS.contains(stop.getStopType())) {
            requireRestStopDone(stop, earlyReason, actorUserId);
        }
    }

    // Quy tắc #32: mọi đơn có ngựa đã nhận lên xe ở điểm đón này phải trả đủ
    private void requirePaid(TripStop stop) {
        Map<Long, String> boarded = new LinkedHashMap<>();
        for (HorseLeg leg : legs.findByBoardTripStop_IdAndStatusIn(stop.getId(), Set.of(HorseLegStatus.IN_PROGRESS))) {
            boarded.put(leg.getBookingHorse().getBooking().getId(), leg.getBookingHorse().getBooking().getCode());
        }
        if (boarded.isEmpty()) return;
        PaymentReadinessService payments = paymentReadiness.getIfAvailable();
        if (payments == null) {
            throw new DispatchRuleException("payment", "Chưa kiểm được thanh toán của đơn nên xe chưa rời nơi nhận được.", true);
        }
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
