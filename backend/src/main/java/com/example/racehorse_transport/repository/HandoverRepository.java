package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.enums.HandoverStatus;
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

    List<Handover> findByTripStop_IdAndHandoverTypeAndStatusOrderById(
            Long tripStopId, com.example.racehorse_transport.entity.enums.HandoverType handoverType, HandoverStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Handover h WHERE h.id = :id")
    Optional<Handover> findForUpdate(@Param("id") Long id);

    // Lần giao đang chờ người nhận đã quá giờ chờ miễn phí (scheduler B5)
    @Query("SELECT h FROM Handover h WHERE h.handoverType = com.example.racehorse_transport.entity.enums.HandoverType.DELIVERY AND h.status = :pending AND h.waitStartedAt IS NOT NULL AND h.waitStartedAt <= :before")
    List<Handover> findWaitingSince(@Param("pending") HandoverStatus pending, @Param("before") java.time.Instant before);
}

