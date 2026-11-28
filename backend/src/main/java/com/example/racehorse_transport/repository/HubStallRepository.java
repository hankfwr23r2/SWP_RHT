package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HubStall;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HubStallRepository extends JpaRepository<HubStall, Long> {
    List<HubStall> findByLocation_IdOrderByCode(Long locationId);

    boolean existsByLocation_IdAndCode(Long locationId, String code);
}
