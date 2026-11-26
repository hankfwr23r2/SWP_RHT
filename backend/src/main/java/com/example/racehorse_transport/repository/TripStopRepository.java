package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.TripStop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripStopRepository extends JpaRepository<TripStop, Long> {

    List<TripStop> findByVehicleTrip_IdOrderBySequenceNumber(Long vehicleTripId);
}
