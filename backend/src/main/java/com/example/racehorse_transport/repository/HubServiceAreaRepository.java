package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HubServiceArea;
import com.example.racehorse_transport.entity.HubServiceAreaId;
import com.example.racehorse_transport.entity.enums.ActiveStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface HubServiceAreaRepository extends JpaRepository<HubServiceArea, HubServiceAreaId> {

    Optional<HubServiceArea> findById_ProvinceCode(String provinceCode);

    @Query("SELECT a FROM HubServiceArea a JOIN FETCH a.hub JOIN FETCH a.province ORDER BY a.province.code")
    List<HubServiceArea> findAllWithHubAndProvince();

    @Query("""
            SELECT h.id FROM HubServiceArea a JOIN a.hub h WHERE a.id.provinceCode = :provinceCode
            AND h.status = :status
            """)
    Optional<Long> findHubId(String provinceCode, ActiveStatus status);
}
