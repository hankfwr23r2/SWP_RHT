package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TripStopRepository extends JpaRepository<TripStop, Long> {
    @Query("SELECT s.vehicleTrip.id FROM TripStop s WHERE s.id = :stopId")
    Optional<Long> findTripId(Long stopId);

    List<TripStop> findByVehicleTrip_IdOrderBySequenceNumber(Long vehicleTripId);

    Optional<TripStop> findFirstByVehicleTrip_IdAndActualArrivalAtIsNotNullOrderBySequenceNumberDesc(Long vehicleTripId);

    boolean existsByIdAndVehicleTrip_StatusAndActualArrivalAtIsNotNullAndActualDepartureAtIsNull(
            Long id, VehicleTripStatus status);

    @EntityGraph(attributePaths = {"location", "bookingAddress", "bookingAddress.booking"})
    List<TripStop> findByVehicleTrip_IdInOrderByVehicleTrip_IdAscSequenceNumberAsc(Collection<Long> vehicleTripIds);
}
