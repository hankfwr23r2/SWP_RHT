package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.LocationType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByCode(String code);

    // Lọc theo loại và trạng thái; tham số null nghĩa là không lọc
    @Query("""
            SELECT l FROM Location l JOIN FETCH l.country LEFT JOIN FETCH l.province
            WHERE (:type IS NULL OR l.type = :type) AND (:status IS NULL OR l.status = :status)
            ORDER BY l.code""")
    List<Location> search(@Param("type") LocationType type, @Param("status") ActiveStatus status);
}
