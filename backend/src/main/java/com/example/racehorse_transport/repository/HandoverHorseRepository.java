package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.HandoverHorseId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HandoverHorseRepository extends JpaRepository<HandoverHorse, HandoverHorseId> {

    List<HandoverHorse> findById_HandoverId(Long handoverId);
}
