package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.DocumentSubject;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.policy.TripReturnPolicy;
import com.example.racehorse_transport.policy.TripReturnPolicy.Progress;
import com.example.racehorse_transport.repository.DocumentRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.repository.VehicleTripRepository;
import com.example.racehorse_transport.service.DocumentService;
import com.example.racehorse_transport.service.NotificationService;
import com.example.racehorse_transport.service.SystemConfigService;
import com.example.racehorse_transport.service.TripReturnService;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class TripReturnServiceImpl implements TripReturnService {
    private static final String PHOTO = "VEHICLE_RETURN_PHOTO";
    private static final String LATE_MINUTES = "RETURN_LATE_MINUTES";
    private static final Set<HorseLegStatus> UNFINISHED = Set.of(HorseLegStatus.PLANNED, HorseLegStatus.IN_PROGRESS);

    private final HorseLegRepository legs;
    private final VehicleTripRepository trips;
    private final TripStopRepository stops;
    private final DocumentRepository documents;
    private final DocumentService files;
    private final NotificationService notifications;
    private final SystemConfigService configs;
    private final Clock clock;
    private final TransactionTemplate transactions;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Progress> progress(List<VehicleTrip> found, Map<Long, List<TripStop>> stopsByTrip) {
        var running = found.stream().filter(trip -> trip.getStatus() == VehicleTripStatus.IN_PROGRESS).toList();
        if (running.isEmpty()) return Map.of();
        Set<Long> unfinished = unfinishedTripIds(running.stream().map(VehicleTrip::getId).toList());
        Instant now = clock.instant();
        int lateMinutes = configs.getInt(LATE_MINUTES);
        return running.stream().collect(Collectors.toMap(VehicleTrip::getId, trip -> TripReturnPolicy.progress(trip.getStatus(),
                stopsByTrip.getOrDefault(trip.getId(), List.of()), unfinished.contains(trip.getId()), now, lateMinutes)));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> photographedTripIds(Collection<Long> tripIds) {
        return tripIds.isEmpty() ? Set.of() : documents.findTripIdsWithPhoto(tripIds, PHOTO);
    }

    // TripCompletionService kiểm quyền, kiểm điểm cuối và giữ khóa dòng lượt xe.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void attachPhoto(Long tripId, MultipartFile file, Long actorId) {
        if (file == null || file.isEmpty() || file.getContentType() == null || !file.getContentType().startsWith("image/")) {
            throw new DispatchRuleException("photo", "Cần tải ảnh xe chụp tại hub.");
        }
        if (!hasPhoto(tripId)) {
            files.attach(file, PHOTO, DocumentSubject.VEHICLE_TRIP, tripId, actorId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requirePhoto(Long tripId) {
        if (!hasPhoto(tripId)) {
            throw new DispatchRuleException("photo", "Chụp ảnh xe tại hub trước khi kết thúc lượt.", true);
        }
    }

    @Override
    public void remindLateReturns() {
        Instant now = clock.instant();
        int lateMinutes = configs.getInt(LATE_MINUTES);
        for (Long id : trips.findReturnReminderCandidates(VehicleTripStatus.IN_PROGRESS, TripReturnPolicy.lateCutoff(now, lateMinutes))) {
            try {
                transactions.executeWithoutResult(status -> remind(id, now, lateMinutes));
            } catch (RuntimeException exception) {
                log.error("Không thể nhắc lượt xe {} về hub trễ", id, exception);
            }
        }
    }

    private void remind(Long id, Instant now, int lateMinutes) {
        var trip = trips.findForUpdate(id).orElse(null);
        if (trip == null || trip.getStatus() != VehicleTripStatus.IN_PROGRESS
                || trip.getReturnLateNotifiedAt() != null || trip.getCoordinator() == null) {
            return;
        }
        Progress progress = TripReturnPolicy.progress(trip.getStatus(), stops.findByVehicleTrip_IdOrderBySequenceNumber(id),
                unfinishedTripIds(List.of(id)).contains(id), now, lateMinutes);
        if (progress.returning() && progress.late()) {
            notifications.notifyAt(List.of(trip.getCoordinator().getUserId()), null, "Xe về trễ",
                    "Lượt " + trip.getCode() + " chưa về hub cuối theo giờ dự kiến.", "/coordinator/dispatch", now);
            trip.setReturnLateNotifiedAt(now);
        }
    }

    private Set<Long> unfinishedTripIds(List<Long> tripIds) {
        return legs.findTripIdsWithUnfinishedLegs(tripIds, UNFINISHED, HorseLegStatus.FAILED, HandoverType.HUB_IN, HandoverStatus.DONE);
    }

    private boolean hasPhoto(Long tripId) {
        return photographedTripIds(List.of(tripId)).contains(tripId);
    }
}
