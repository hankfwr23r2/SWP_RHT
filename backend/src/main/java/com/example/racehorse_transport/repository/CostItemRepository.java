package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.CostItem;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CostItemRepository extends JpaRepository<CostItem, Long> {
}
