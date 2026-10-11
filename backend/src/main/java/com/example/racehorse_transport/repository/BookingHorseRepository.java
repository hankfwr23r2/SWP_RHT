package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.BookingHorseId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingHorseRepository extends JpaRepository<BookingHorse, BookingHorseId> {
}
