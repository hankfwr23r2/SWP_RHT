package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Integer> {
    List<Booking> findByCustomerID_IdOrderByBookingDateDesc(Integer customerId);
    List<Booking> findByStatusOrderByBookingDateAsc(String status);
    List<Booking> findAllByOrderByBookingDateDesc();
}
