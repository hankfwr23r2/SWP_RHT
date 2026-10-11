package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Customer;

import java.util.List;
import java.util.Optional;

public interface CustomerService {
    List<Customer> findAll();
    Optional<Customer> findById(Integer id);
    Customer save(Customer entity);
    void deleteById(Integer id);
}
