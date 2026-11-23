package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
