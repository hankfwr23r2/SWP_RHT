package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.RouteCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface RouteCacheRepository extends JpaRepository<RouteCacheEntry, String> {

    // Ghi đè nếu khóa đã có: hai lần xem trước cùng tuyến cùng lúc không bị lỗi trùng khóa
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO route_cache (cache_key, payload, created_at) VALUES (:key, :payload, :createdAt) "
            + "ON CONFLICT (cache_key) DO UPDATE SET payload = EXCLUDED.payload, created_at = EXCLUDED.created_at",
            nativeQuery = true)
    void upsert(@Param("key") String key, @Param("payload") String payload, @Param("createdAt") Instant createdAt);
}
