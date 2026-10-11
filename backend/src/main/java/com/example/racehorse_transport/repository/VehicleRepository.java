package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Integer> {
    List<Vehicle> findByStatus(String status);
    Optional<Vehicle> findByLicensePlate(String licensePlate);
    Optional<Vehicle> findByVehicleCode(String vehicleCode);
}
