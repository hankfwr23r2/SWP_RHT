package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Horse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HorseRepository extends JpaRepository<Horse, Long> {

    Optional<Horse> findByMicrochip(String microchip);
}
