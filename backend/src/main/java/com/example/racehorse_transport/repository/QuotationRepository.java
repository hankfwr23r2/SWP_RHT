package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Quotation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {

    List<Quotation> findByBooking_IdOrderByVersion(Long bookingId);
}
