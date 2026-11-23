package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Vehicle;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    Optional<Vehicle> findByLicensePlate(String licensePlate);

    List<Vehicle> findByLicensePlateIgnoreCase(String licensePlate);

    @EntityGraph(attributePaths = {"vehicleClass", "homeHubLocation"})
    List<Vehicle> findAllByOrderByLicensePlateAsc();

    @EntityGraph(attributePaths = {"vehicleClass", "homeHubLocation"})
    Optional<Vehicle> findOneById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.id = :id")
    Optional<Vehicle> findForUpdate(@Param("id") Long id);
}
