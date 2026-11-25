package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.enums.VehicleTripType;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleTripRepository extends JpaRepository<VehicleTrip, Long> {
    @Query("""
            SELECT t.id FROM VehicleTrip t WHERE t.status = :status AND t.tripType IN :types
                AND EXISTS (SELECT s.id FROM TripStop s WHERE s.vehicleTrip = t
                    AND s.actualArrivalAt IS NOT NULL AND s.actualDepartureAt IS NULL
                    AND EXISTS (SELECT p.id FROM TripStop p WHERE p.vehicleTrip = t
                        AND p.sequenceNumber < s.sequenceNumber AND p.actualDepartureAt IS NOT NULL))
            ORDER BY t.id
            """)
    List<Long> findHeldTripIds(VehicleTripStatus status, Collection<VehicleTripType> types);

    @Query("SELECT MIN(t.actualStartAt) FROM VehicleTrip t WHERE t.departure.id = :departureId")
    Optional<Instant> firstActualStartForDeparture(Long departureId);
    boolean existsByVehicle_IdAndStatusNotIn(Long vehicleId, Collection<VehicleTripStatus> statuses);

    boolean existsByDeparture_IdAndTripTypeAndStatusNotIn(Long departureId,
            VehicleTripType tripType, Collection<VehicleTripStatus> statuses);

    boolean existsByCode(String code);

    @Query("""
            SELECT t.id FROM VehicleTrip t WHERE t.status <> com.example.racehorse_transport.entity.enums.VehicleTripStatus.CANCELLED
            AND ((t.departure.id = :departureId AND t.tripType = com.example.racehorse_transport.entity.enums.VehicleTripType.LINEHAUL)
                OR EXISTS (SELECT l.id FROM HorseLeg l WHERE l.vehicleTrip = t AND l.bookingHorse.booking.departure.id = :departureId
                    AND l.legType = com.example.racehorse_transport.entity.enums.HorseLegType.DELIVERY
                    AND l.status = com.example.racehorse_transport.entity.enums.HorseLegStatus.PLANNED))
            ORDER BY t.id""")
    List<Long> findIdsForDelayShift(Long departureId);

    // Mọi lượt chưa hủy của xe, để kiểm trùng giờ và nơi lượt trước kết thúc
    List<VehicleTrip> findByVehicle_IdAndStatusNot(Long vehicleId, VehicleTripStatus status);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.vehicleClass", "departure", "departure.route", "startLocation", "endLocation"})
    @Query("SELECT t FROM VehicleTrip t WHERE t.plannedStartAt < :to AND t.plannedEndAt > :from ORDER BY t.plannedStartAt, t.id")
    List<VehicleTrip> findOverlapping(@Param("from") Instant from, @Param("to") Instant to);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.vehicleClass", "departure", "departure.route", "startLocation", "endLocation"})
    Optional<VehicleTrip> findOneById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM VehicleTrip t WHERE t.id = :id")
    Optional<VehicleTrip> findForUpdate(@Param("id") Long id);

    // Báo cáo chuyến: lượt xe bắt đầu (kế hoạch) trong khoảng, bỏ lượt đã hủy
    @EntityGraph(attributePaths = {"vehicle", "startLocation", "endLocation"})
    List<VehicleTrip> findByPlannedStartAtGreaterThanEqualAndPlannedStartAtLessThanAndStatusNotOrderByPlannedStartAtAscIdAsc(
            Instant from, Instant to, VehicleTripStatus excluded);
}
