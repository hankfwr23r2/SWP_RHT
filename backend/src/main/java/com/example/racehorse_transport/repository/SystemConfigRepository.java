package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.SystemConfig;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, String> {

    @EntityGraph(attributePaths = "updatedBy")
    List<SystemConfig> findAllByOrderByConfigKeyAsc();

    @EntityGraph(attributePaths = "updatedBy")
    Optional<SystemConfig> findOneByConfigKey(String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SystemConfig c where c.configKey = :key")
    Optional<SystemConfig> findForUpdate(@Param("key") String key);
}
