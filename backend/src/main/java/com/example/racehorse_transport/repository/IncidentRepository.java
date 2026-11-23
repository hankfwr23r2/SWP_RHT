package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Incident;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
}
