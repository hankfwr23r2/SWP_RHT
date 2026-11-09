package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BookingHorseId;
import com.example.racehorse_transport.entity.BookingHorseInsurance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingHorseInsuranceRepository extends JpaRepository<BookingHorseInsurance, Long> {
    Optional<BookingHorseInsurance> findByBookingHorse_Id(BookingHorseId id);
}
