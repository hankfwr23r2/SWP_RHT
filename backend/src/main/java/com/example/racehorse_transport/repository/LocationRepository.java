package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.LocationType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    @Override
    @EntityGraph(attributePaths = {"country", "province", "counterpartCountry"})
    Optional<Location> findById(Long id);

    Optional<Location> findByCode(String code);

    @Query(value = "SELECT * FROM location WHERE id = :id FOR NO KEY UPDATE", nativeQuery = true)
    Optional<Location> findForUpdate(Long id);

    // Lọc theo loại và trạng thái; tham số null nghĩa là không lọc
    @Query("""
            SELECT l FROM Location l JOIN FETCH l.country LEFT JOIN FETCH l.province LEFT JOIN FETCH l.counterpartCountry
            WHERE (:locationType IS NULL OR l.locationType = :locationType) AND (:status IS NULL OR l.status = :status)
            ORDER BY l.code""")
    List<Location> search(@Param("locationType") LocationType locationType, @Param("status") ActiveStatus status);
}
