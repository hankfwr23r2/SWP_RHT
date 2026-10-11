package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Integer> {
}
