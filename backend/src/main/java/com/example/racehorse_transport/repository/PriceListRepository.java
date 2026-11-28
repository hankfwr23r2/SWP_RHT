package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.PriceList;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceListRepository extends JpaRepository<PriceList, Long> {
}
