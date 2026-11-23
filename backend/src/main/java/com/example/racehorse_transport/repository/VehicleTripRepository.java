package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleTripRepository extends JpaRepository<VehicleTrip, Long> {

    boolean existsByVehicle_IdAndStatusNotIn(Long vehicleId, Collection<VehicleTripStatus> statuses);
}
