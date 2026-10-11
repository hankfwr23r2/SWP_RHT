package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Payment;

import java.util.List;
import java.util.Optional;

public interface PaymentService {
    List<Payment> findAll();
    Optional<Payment> findById(Integer id);
    Payment save(Payment entity);
    void deleteById(Integer id);
}
