package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    List<Payment> findByBookingID_IdOrderByPaymentDateDesc(Integer bookingId);
}
