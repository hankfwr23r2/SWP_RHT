package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.RouteCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteCacheRepository extends JpaRepository<RouteCacheEntry, String> {
}
