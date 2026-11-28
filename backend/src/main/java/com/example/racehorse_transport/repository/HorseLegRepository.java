package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HorseLeg;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HorseLegRepository extends JpaRepository<HorseLeg, Long> {

    List<HorseLeg> findByBookingHorse_IdOrderBySequenceNumber(Long bookingHorseId);
}
