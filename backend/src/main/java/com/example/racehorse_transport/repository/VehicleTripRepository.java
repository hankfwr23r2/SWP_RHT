package com.example.racehorse_transport.repository;

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
    boolean existsByVehicle_IdAndStatusNotIn(Long vehicleId, Collection<VehicleTripStatus> statuses);

    boolean existsByCode(String code);

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
}
