package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HorseInsurance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HorseInsuranceRepository extends JpaRepository<HorseInsurance, Long> {

    Optional<HorseInsurance> findByBookingHorse_Id(Long bookingHorseId);
}
