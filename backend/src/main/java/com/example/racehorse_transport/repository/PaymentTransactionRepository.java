package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.PaymentTransaction;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
}
