package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BookingHorse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingHorseRepository extends JpaRepository<BookingHorse, Long> {

    List<BookingHorse> findByBooking_Id(Long bookingId);
}
