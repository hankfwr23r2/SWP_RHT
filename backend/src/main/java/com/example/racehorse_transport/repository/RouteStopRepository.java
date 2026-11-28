package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.RouteStop;
import com.example.racehorse_transport.entity.RouteStopId;
import com.example.racehorse_transport.entity.enums.ActiveStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RouteStopRepository extends JpaRepository<RouteStop, RouteStopId> {

    List<RouteStop> findById_RouteIdOrderById_SequenceNumber(Long routeId);

    @Query("SELECT s FROM RouteStop s JOIN FETCH s.location WHERE s.id.routeId = :routeId ORDER BY s.id.sequenceNumber")
    List<RouteStop> findWithLocation(@Param("routeId") Long routeId);

    @Query("""
            SELECT s FROM RouteStop s JOIN FETCH s.route r JOIN FETCH s.location
            WHERE r.code = (SELECT min(candidate.code) FROM Route candidate
                WHERE candidate.origin.id = :originId AND candidate.destination.id = :destinationId AND candidate.status = :status)
            ORDER BY s.id.sequenceNumber
            """)
    List<RouteStop> findBetween(@Param("originId") Long originId, @Param("destinationId") Long destinationId,
                                @Param("status") ActiveStatus status);

    // Cửa khẩu tuyến đi qua, theo thứ tự trên tuyến
    @Query("""
            SELECT s.location.id FROM RouteStop s
            WHERE s.id.routeId = :routeId AND s.stopType = com.example.racehorse_transport.entity.enums.RouteStopType.BORDER
            ORDER BY s.id.sequenceNumber
            """)
    List<Long> findBorderLocationIds(@Param("routeId") Long routeId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RouteStop s WHERE s.id.routeId = :routeId")
    int deleteByRouteId(@Param("routeId") Long routeId);
}
