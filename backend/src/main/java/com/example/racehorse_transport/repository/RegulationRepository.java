package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Regulation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegulationRepository extends JpaRepository<Regulation, Integer> {
}
