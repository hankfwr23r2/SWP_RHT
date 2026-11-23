package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    boolean existsByVehicleTrip_IdAndHorseLegIsNullAndStatusIn(Long vehicleTripId, Collection<IncidentStatus> statuses);

    @EntityGraph(attributePaths = {"booking", "vehicleTrip", "horseLeg", "horseLeg.bookingHorse", "horseLeg.bookingHorse.horse", "reportedBy", "fitConfirmedBy"})
    List<Incident> findByStatusInOrderByReportedAtDesc(Collection<IncidentStatus> statuses);

    @EntityGraph(attributePaths = {"booking", "vehicleTrip", "horseLeg", "horseLeg.bookingHorse", "horseLeg.bookingHorse.horse", "reportedBy", "fitConfirmedBy"})
    List<Incident> findByVehicleTrip_IdOrderByReportedAtDesc(Long vehicleTripId);

    @EntityGraph(attributePaths = {"booking", "vehicleTrip", "horseLeg", "horseLeg.bookingHorse", "horseLeg.bookingHorse.horse", "reportedBy", "fitConfirmedBy"})
    Optional<Incident> findOneById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Incident i WHERE i.id = :id")
    Optional<Incident> findForUpdate(@Param("id") Long id);
}
