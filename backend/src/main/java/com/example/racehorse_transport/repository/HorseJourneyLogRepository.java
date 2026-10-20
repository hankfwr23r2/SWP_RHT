package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HorseJourneyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HorseJourneyLogRepository extends JpaRepository<HorseJourneyLog, Long> {
    List<HorseJourneyLog> findByHorse_IdOrderByCreatedAtDesc(Integer horseId);
    boolean existsByHorse_Id(Integer horseId);
}
