package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HorseLegRepository extends JpaRepository<HorseLeg, Long> {
    List<HorseLeg> findByBookingHorse_IdOrderBySequenceNumber(Long bookingHorseId);

    boolean existsByVehicleTrip_IdAndStatusNot(Long vehicleTripId, HorseLegStatus status);
}
