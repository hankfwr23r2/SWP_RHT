package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HorseInsurance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface HorseInsuranceRepository extends JpaRepository<HorseInsurance, Long> {

    Optional<HorseInsurance> findByBookingHorse_Id(Long bookingHorseId);

    @Query("""
            SELECT i FROM HorseInsurance i LEFT JOIN FETCH i.insurerParty LEFT JOIN FETCH i.waiverTermsAcceptance
            WHERE i.bookingHorse.id IN :horseRowIds
            """)
    List<HorseInsurance> findForDetail(List<Long> horseRowIds);
}
