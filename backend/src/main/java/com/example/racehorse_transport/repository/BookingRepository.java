package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Booking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByCode(String code);
}
