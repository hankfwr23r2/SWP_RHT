package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.HandoverHorseId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface HandoverHorseRepository extends JpaRepository<HandoverHorse, HandoverHorseId> {

    List<HandoverHorse> findById_HandoverId(Long handoverId);

    // Theo dõi hành trình: mọi lần giao nhận của các con trong một đơn
    @Query("select hh from HandoverHorse hh join fetch hh.handover h join fetch hh.horseLeg l where l.bookingHorse.id in :bookingHorseIds order by h.id")
    List<HandoverHorse> findByBookingHorseIds(Collection<Long> bookingHorseIds);
}
