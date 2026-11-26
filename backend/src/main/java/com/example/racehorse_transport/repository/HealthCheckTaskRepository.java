package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HealthCheckTask;
import com.example.racehorse_transport.entity.enums.HealthCheckTaskStatus;
import com.example.racehorse_transport.entity.enums.HealthCheckType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface HealthCheckTaskRepository extends JpaRepository<HealthCheckTask, Long> {
    @EntityGraph("HealthCheckTask.details")
    @Query("""
            select t from HealthCheckTask t where t.assignedTo.userId = :id
            and (t.status in :open or (t.status = com.example.racehorse_transport.entity.enums.HealthCheckTaskStatus.DONE
                and t.checkType in :exitTypes and exists (select s.id from HorseStay s where s.bookingHorse = t.bookingHorse
                    and s.actualStartAt is not null and s.actualEndAt is null)))
            order by t.dueAt, t.id
            """)
    List<HealthCheckTask> findMine(Long id, Collection<HealthCheckTaskStatus> open, Collection<HealthCheckType> exitTypes);

    @EntityGraph("HealthCheckTask.details")
    List<HealthCheckTask> findByBookingHorse_IdOrderByDueAt(Long id);

    @EntityGraph("HealthCheckTask.details")
    List<HealthCheckTask> findByBookingHorse_IdInAndCheckTypeInAndStatusNotOrderByDueAt(
            Collection<Long> horseIds, Collection<HealthCheckType> types, HealthCheckTaskStatus status);

    @EntityGraph("HealthCheckTask.details")
    Optional<HealthCheckTask> findOneById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from HealthCheckTask t where t.id = :id")
    Optional<HealthCheckTask> findForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from HealthCheckTask t where t.assignedTo.userId = :staffId
            and t.bookingHorse.id = :horseId and t.status in :statuses and t.assignedAt <= :recordedAt
            and (t.horseStay.id = :stayId or t.vehicleTrip.id = :tripId
                 or (t.checkType in :exitTypes and t.vehicleTrip.id in :boardingTripIds))
            order by t.id
            """)
    List<HealthCheckTask> findForLog(Long staffId, Long horseId, Long stayId, Long tripId,
                                     Instant recordedAt, Collection<HealthCheckTaskStatus> statuses,
                                     Collection<HealthCheckType> exitTypes, Collection<Long> boardingTripIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from HealthCheckTask t where t.bookingHorse.id = :horseId and t.checkType = :type
            and t.status <> com.example.racehorse_transport.entity.enums.HealthCheckTaskStatus.CANCELLED
            and (t.vehicleTrip.id = :tripId or t.horseStay.id = :stayId)
            order by t.id
            """)
    List<HealthCheckTask> findExistingForUpdate(Long horseId, HealthCheckType type, Long tripId, Long stayId);

    @Query("""
            select count(t) > 0 from HealthCheckTask t where t.bookingHorse.id = :horseId and t.checkType = :type
            and t.status = com.example.racehorse_transport.entity.enums.HealthCheckTaskStatus.DONE
            and (t.vehicleTrip.id = :tripId or t.horseStay.id = :stayId)
            """)
    boolean hasCompletedCheck(Long horseId, HealthCheckType type, Long tripId, Long stayId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<HealthCheckTask> findByOverdueNotifiedAtIsNullAndDueAtBeforeAndStatusInOrderById(
            Instant now, Collection<HealthCheckTaskStatus> statuses);
}
