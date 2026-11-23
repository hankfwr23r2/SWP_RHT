package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.TripStop;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripStopRepository extends JpaRepository<TripStop, Long> {
    List<TripStop> findByVehicleTrip_IdOrderBySequenceNumber(Long vehicleTripId);

    @EntityGraph(attributePaths = {"location", "bookingAddress", "bookingAddress.booking"})
    List<TripStop> findByVehicleTrip_IdInOrderByVehicleTrip_IdAscSequenceNumberAsc(Collection<Long> vehicleTripIds);
}
