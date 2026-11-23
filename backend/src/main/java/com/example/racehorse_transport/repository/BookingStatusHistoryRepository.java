package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BookingStatusHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, Long> {

    Optional<BookingStatusHistory> findFirstByBooking_IdOrderByChangedAtDescIdDesc(Long bookingId);

    List<BookingStatusHistory> findByBooking_IdOrderByChangedAtAscIdAsc(Long bookingId);
}
