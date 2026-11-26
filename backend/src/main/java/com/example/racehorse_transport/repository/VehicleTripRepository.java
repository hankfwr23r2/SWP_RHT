package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.VehicleTrip;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleTripRepository extends JpaRepository<VehicleTrip, Long> {
}
