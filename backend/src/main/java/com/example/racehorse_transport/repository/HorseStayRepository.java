package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HorseStay;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HorseStayRepository extends JpaRepository<HorseStay, Long> {

    // Lưu trú đang diễn ra của một con (đã vào, chưa ra)
    Optional<HorseStay> findFirstByBookingHorse_IdAndActualStartAtIsNotNullAndActualEndAtIsNull(Long bookingHorseId);

    @EntityGraph(attributePaths = {"bookingHorse", "bookingHorse.horse", "bookingHorse.booking", "location"})
    List<HorseStay> findByLocation_IdAndActualStartAtIsNotNullAndActualEndAtIsNullOrderByActualStartAt(Long locationId);
}
