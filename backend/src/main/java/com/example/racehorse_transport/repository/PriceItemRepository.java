package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.PriceItem;
import com.example.racehorse_transport.entity.BillableService;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PriceItemRepository extends JpaRepository<PriceItem, Long> {
    @Query("select s from BillableService s where s.status = :status order by s.code")
    List<BillableService> findPricingCatalog(ActiveStatus status);

    @Query("select s.code from StallType s")
    List<String> findStallTypeCodes();

    @EntityGraph(attributePaths = {"priceList", "billableService", "route", "vehicleClass", "stallType"})
    List<PriceItem> findByPriceList_Id(Long priceListId);

    @EntityGraph(attributePaths = {"priceList", "billableService", "route", "vehicleClass", "stallType"})
    Optional<PriceItem> findByIdAndPriceList_Id(Long id, Long priceListId);
}
