package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.ActiveStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {

    Optional<Route> findByCode(String code);

    @Query("SELECT r FROM Route r JOIN FETCH r.origin JOIN FETCH r.destination ORDER BY r.code")
    List<Route> findAllWithEndpoints();

    @Query("SELECT r FROM Route r JOIN FETCH r.origin JOIN FETCH r.destination WHERE r.id = :id")
    Optional<Route> findWithEndpoints(@Param("id") Long id);

    // Địa điểm đang là điểm đầu, điểm cuối hoặc điểm dừng của một tuyến ở trạng thái cho trước
    @Query("""
            SELECT count(r) > 0 FROM Route r
            WHERE r.status = :status AND (r.origin.id = :locationId OR r.destination.id = :locationId
                  OR EXISTS (SELECT 1 FROM RouteStop s WHERE s.route = r AND s.location.id = :locationId))""")
    boolean isUsedByRoute(@Param("locationId") Long locationId, @Param("status") ActiveStatus status);
}
