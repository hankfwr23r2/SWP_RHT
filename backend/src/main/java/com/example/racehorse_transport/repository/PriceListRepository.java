package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.PriceList;
import com.example.racehorse_transport.entity.enums.PriceListStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PriceListRepository extends JpaRepository<PriceList, Long> {
    List<PriceList> findAllByOrderByIdAsc();

    List<PriceList> findByCurrencyCodeAndStatus(String currencyCode, PriceListStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PriceList p where p.id = :id")
    Optional<PriceList> findForUpdate(Long id);

    @Query(value = "select 1 from pg_advisory_xact_lock(hashtext('price_list'), hashtext(:currencyCode))", nativeQuery = true)
    int lockActivationCurrency(String currencyCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PriceList> findByCurrencyCodeAndStatusAndValidToIsNull(String currencyCode, PriceListStatus status);

    @Query(value = """
            select exists (select 1 from quotation where price_list_id = :id)
                or exists (select 1 from quotation_line q join price_item p on p.id = q.price_item_id
                           where p.price_list_id = :id)
            """, nativeQuery = true)
    boolean isReferenced(Long id);
}
