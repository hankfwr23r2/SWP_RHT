package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HorseType;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HorseTypeRepository extends JpaRepository<HorseType, Long> {
}
