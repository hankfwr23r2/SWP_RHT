package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.LocationChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationChangeLogRepository extends JpaRepository<LocationChangeLog, Long> {
}
