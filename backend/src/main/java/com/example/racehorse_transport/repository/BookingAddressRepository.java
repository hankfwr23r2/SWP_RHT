package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BookingAddress;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingAddressRepository extends JpaRepository<BookingAddress, Long> {

    List<BookingAddress> findByBooking_Id(Long bookingId);
}
