package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BookingHorseAddOn;
import com.example.racehorse_transport.entity.BookingHorseAddOnId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BookingHorseAddOnRepository extends JpaRepository<BookingHorseAddOn, BookingHorseAddOnId> {

    List<BookingHorseAddOn> findById_BookingHorseId(Long bookingHorseId);

    @Query("""
            SELECT a FROM BookingHorseAddOn a JOIN FETCH a.billableService
            WHERE a.bookingHorse.id IN :horseRowIds ORDER BY a.id.billableServiceId
            """)
    List<BookingHorseAddOn> findAddOnsForDetail(List<Long> horseRowIds);
}
