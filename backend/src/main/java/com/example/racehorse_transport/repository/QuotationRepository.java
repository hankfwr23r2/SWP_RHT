package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Quotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuotationRepository extends JpaRepository<Quotation, Integer> {
    List<Quotation> findByBookingIdOrderBySentAtDesc(Integer bookingId);
    Optional<Quotation> findTopByBookingIdOrderBySentAtDesc(Integer bookingId);
}
