package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Location;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByCode(String code);
}
