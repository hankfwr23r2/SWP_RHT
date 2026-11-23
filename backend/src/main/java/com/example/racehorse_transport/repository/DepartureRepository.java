package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Departure;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartureRepository extends JpaRepository<Departure, Long> {
}
