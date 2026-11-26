package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BillableService;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BillableServiceRepository extends JpaRepository<BillableService, Long> {

    Optional<BillableService> findByCode(String code);
}
