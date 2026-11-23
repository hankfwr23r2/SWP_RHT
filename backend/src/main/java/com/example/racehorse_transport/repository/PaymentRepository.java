package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByBooking_IdOrderById(Long bookingId);
}
