package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
import com.example.racehorse_transport.entity.enums.HandoverType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HandoverRepository extends JpaRepository<Handover, Long> {

    interface CompletionContext {
        Long getTripId();
        Long getPlanId();
    }

    @Query("""
            SELECT s.vehicleTrip.id AS tripId, p.id AS planId FROM Handover h
            JOIN h.tripStop s LEFT JOIN h.incidentPlan p
            WHERE h.id = :id
            """)
    Optional<CompletionContext> findCompletionContext(Long id);

    List<Handover> findByIncidentPlan_Incident_IdAndStatusOrderById(Long incidentId, HandoverStatus status);

    List<Handover> findByTripStop_VehicleTrip_IdAndHandoverTypeAndStatusAndIncidentPlanIsNullOrderById(
            Long tripId, HandoverType handoverType, HandoverStatus status);

    @Query("""
            SELECT h.id FROM Handover h WHERE h.status = com.example.racehorse_transport.entity.enums.HandoverStatus.PENDING
                AND h.handoverType = com.example.racehorse_transport.entity.enums.HandoverType.HUB_OUT
                AND EXISTS (SELECT l.id FROM HorseLeg l WHERE l.boardTripStop = h.tripStop
                    AND l.bookingHorse.booking.id = :bookingId
                    AND l.status = com.example.racehorse_transport.entity.enums.HorseLegStatus.PLANNED)
            ORDER BY h.id
            """)
    List<Long> findPendingHubOutIds(Long bookingId);

    @EntityGraph(attributePaths = {"tripStop", "handledBy"})
    List<Handover> findByTripStop_IdInOrderById(Collection<Long> tripStopIds);

    long countByTripStop_IdAndStatus(Long tripStopId, HandoverStatus status);

    boolean existsByTripStop_IdAndStatusAndHandoverTypeIn(Long tripStopId, HandoverStatus status, Collection<HandoverType> types);

    List<Handover> findByTripStop_IdAndHandoverTypeAndStatusOrderById(
            Long tripStopId, com.example.racehorse_transport.entity.enums.HandoverType handoverType, HandoverStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Handover h WHERE h.id = :id")
    Optional<Handover> findForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // Lần giao đang chờ người nhận đã quá giờ chờ miễn phí (scheduler B5)
    @Query("SELECT h FROM Handover h WHERE h.handoverType = com.example.racehorse_transport.entity.enums.HandoverType.DELIVERY AND h.status = :pending AND h.waitStartedAt IS NOT NULL AND h.waitStartedAt <= :before ORDER BY h.id")
    List<Handover> findWaitingSince(@Param("pending") HandoverStatus pending, @Param("before") java.time.Instant before);
}

