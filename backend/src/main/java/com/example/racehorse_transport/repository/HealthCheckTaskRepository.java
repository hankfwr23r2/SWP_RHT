package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HealthCheckTask;
import com.example.racehorse_transport.entity.enums.HealthCheckTaskStatus;
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
    List<HealthCheckTask> findByAssignedTo_UserIdAndStatusInOrderByDueAt(Long id, Collection<HealthCheckTaskStatus> statuses);

    @EntityGraph("HealthCheckTask.details")
    List<HealthCheckTask> findByBookingHorse_IdOrderByDueAt(Long id);

    @EntityGraph("HealthCheckTask.details")
    Optional<HealthCheckTask> findOneById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from HealthCheckTask t where t.id = :id")
    Optional<HealthCheckTask> findForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from HealthCheckTask t where t.assignedTo.userId = :staffId
            and t.bookingHorse.id = :horseId and t.status in :statuses and t.assignedAt <= :recordedAt
            and (t.horseStay.id = :stayId or t.vehicleTrip.id = :tripId)
            order by t.id
            """)
    List<HealthCheckTask> findForLog(Long staffId, Long horseId, Long stayId, Long tripId,
                                     Instant recordedAt, Collection<HealthCheckTaskStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<HealthCheckTask> findByOverdueNotifiedAtIsNullAndDueAtBeforeAndStatusInOrderById(
            Instant now, Collection<HealthCheckTaskStatus> statuses);
}
