package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BookingHorseAddOn;
import com.example.racehorse_transport.entity.BookingHorseAddOnId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingHorseAddOnRepository extends JpaRepository<BookingHorseAddOn, BookingHorseAddOnId> {

    List<BookingHorseAddOn> findById_BookingHorseId(Long bookingHorseId);
}
