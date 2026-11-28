package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BookingParty;
import com.example.racehorse_transport.entity.BookingPartyId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingPartyRepository extends JpaRepository<BookingParty, BookingPartyId> {

    List<BookingParty> findById_BookingId(Long bookingId);
}
