package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.PriceItem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceItemRepository extends JpaRepository<PriceItem, Long> {

    List<PriceItem> findByPriceList_Id(Long priceListId);
}
