package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BillableService;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillableServiceRepository extends JpaRepository<BillableService, Long> {
    Optional<BillableService> findByCode(String code);

    List<BillableService> findByStatusOrderByCodeAsc(ActiveStatus status);
}
