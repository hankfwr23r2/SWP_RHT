package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.VehicleClass;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VehicleClassRepository extends JpaRepository<VehicleClass, Long> {

    Optional<VehicleClass> findByCode(String code);
}
