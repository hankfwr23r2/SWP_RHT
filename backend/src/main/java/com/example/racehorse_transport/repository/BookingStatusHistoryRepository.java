package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BookingStatusHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Long> {

    List<BookingStatusHistory> findByBooking_IdOrderByChangedAtAscIdAsc(Long bookingId);
}
