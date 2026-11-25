package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.enums.IncidentType;

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

    @Override
    @Query("SELECT i FROM Incident i WHERE i.id = :id AND i.incidentType <> com.example.racehorse_transport.entity.enums.IncidentType.COMPLAINT")
    Optional<Incident> findById(@Param("id") Long id);

    @Override
    @Query("SELECT count(i) > 0 FROM Incident i WHERE i.id = :id AND i.incidentType <> com.example.racehorse_transport.entity.enums.IncidentType.COMPLAINT")
    boolean existsById(@Param("id") Long id);

    // Con giao thất bại do không gặp người nhận (B5) thì giữ có phí; về hub do sự cố khác thì tập kết không phí
    boolean existsByHorseLeg_IdAndIncidentType(Long horseLegId, IncidentType incidentType);

    boolean existsByVehicleTrip_IdAndHorseLegIsNullAndStatusIn(Long vehicleTripId, Collection<IncidentStatus> statuses);

    @EntityGraph(attributePaths = {"booking", "vehicleTrip", "horseLeg", "horseLeg.bookingHorse", "horseLeg.bookingHorse.horse", "reportedBy", "fitConfirmedBy"})
    @Query("SELECT i FROM Incident i WHERE i.status IN :statuses AND i.incidentType <> com.example.racehorse_transport.entity.enums.IncidentType.COMPLAINT ORDER BY i.reportedAt DESC")
    List<Incident> findByStatusInOrderByReportedAtDesc(Collection<IncidentStatus> statuses);

    @EntityGraph(attributePaths = {"booking", "vehicleTrip", "horseLeg", "horseLeg.bookingHorse", "horseLeg.bookingHorse.horse", "reportedBy", "fitConfirmedBy"})
    List<Incident> findByVehicleTrip_IdOrderByReportedAtDesc(Long vehicleTripId);

    @EntityGraph(attributePaths = {"booking", "vehicleTrip", "horseLeg", "horseLeg.bookingHorse", "horseLeg.bookingHorse.horse", "reportedBy", "fitConfirmedBy"})
    @Query("SELECT i FROM Incident i WHERE i.id = :id AND i.incidentType <> com.example.racehorse_transport.entity.enums.IncidentType.COMPLAINT")
    Optional<Incident> findOneById(Long id);

    @EntityGraph(attributePaths = {"booking", "bookingHorse"})
    Optional<Incident> findByBooking_IdAndIncidentType(Long bookingId, IncidentType incidentType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Incident i WHERE i.id = :id AND i.incidentType <> com.example.racehorse_transport.entity.enums.IncidentType.COMPLAINT")
    Optional<Incident> findForUpdate(@Param("id") Long id);

    @Query("""
            SELECT count(i) > 0 FROM Incident i
            LEFT JOIN i.booking b LEFT JOIN i.horseLeg affected LEFT JOIN i.vehicleTrip affectedTrip
            LEFT JOIN affected.bookingHorse affectedHorse LEFT JOIN affectedHorse.booking affectedBooking
            WHERE i.status <> :closed AND (b.id = :bookingId OR affectedBooking.id = :bookingId
                OR (affected IS NULL AND EXISTS (SELECT 1 FROM HorseLeg l
                    WHERE l.bookingHorse.booking.id = :bookingId AND l.vehicleTrip = affectedTrip)))
            """)
    boolean hasIncidentForBookingOtherThan(@Param("bookingId") Long bookingId, @Param("closed") IncidentStatus closed);

    @Query("""
            SELECT count(d) > 0 FROM Document d
            WHERE d.incident.id = :incidentId AND d.documentType.code = :code
            """)
    boolean hasDocumentType(@Param("incidentId") Long incidentId, @Param("code") String code);

    // Báo cáo chuyến
    List<Incident> findByVehicleTrip_IdInOrderByReportedAt(Collection<Long> vehicleTripIds);
}
