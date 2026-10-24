package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {
    List<SystemConfig> findByConfigGroupOrderByConfigKeyAsc(String configGroup);
}
