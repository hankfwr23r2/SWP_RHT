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

    @Query("SELECT r.id FROM Route r WHERE r.origin.id = :originId AND r.destination.id = :destinationId AND r.fleetOutbound = true")
    List<Long> fleetOutboundIds(Long originId, Long destinationId);

    @Query("SELECT r FROM Route r JOIN FETCH r.origin JOIN FETCH r.destination ORDER BY r.code")
    List<Route> findAllWithEndpoints();

    @Query("SELECT r FROM Route r JOIN FETCH r.origin JOIN FETCH r.destination WHERE r.id = :id")
    Optional<Route> findWithEndpoints(@Param("id") Long id);

    @Query("SELECT r FROM Route r JOIN FETCH r.origin o JOIN FETCH r.destination dest LEFT JOIN FETCH o.counterpartCountry LEFT JOIN FETCH dest.counterpartCountry WHERE r.status = :status")
    List<Route> findByStatusWithEndpoints(@Param("status") ActiveStatus status);

    // Địa điểm đang là điểm đầu, điểm cuối hoặc điểm dừng của một tuyến ở trạng thái cho trước
    @Query("""
            SELECT count(r) > 0 FROM Route r
            WHERE r.status = :status AND (r.origin.id = :locationId OR r.destination.id = :locationId
                  OR EXISTS (SELECT 1 FROM RouteStop s WHERE s.route = r AND s.location.id = :locationId))""")
    boolean isUsedByRoute(@Param("locationId") Long locationId, @Param("status") ActiveStatus status);

    @Query("""
            SELECT r FROM Route r JOIN FETCH r.origin o JOIN FETCH r.destination dest LEFT JOIN FETCH o.counterpartCountry LEFT JOIN FETCH dest.counterpartCountry
            WHERE r.id = (SELECT d.route.id FROM Departure d WHERE d.id = :departureId)
            """)
    Optional<Route> findRouteForSubmission(Long departureId);
}
