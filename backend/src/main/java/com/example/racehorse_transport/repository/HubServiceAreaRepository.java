package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HubServiceArea;
import com.example.racehorse_transport.entity.HubServiceAreaId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HubServiceAreaRepository extends JpaRepository<HubServiceArea, HubServiceAreaId> {

    Optional<HubServiceArea> findById_ProvinceCode(String provinceCode);
}
