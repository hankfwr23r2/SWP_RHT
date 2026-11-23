package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Handover;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HandoverRepository extends JpaRepository<Handover, Long> {
}
