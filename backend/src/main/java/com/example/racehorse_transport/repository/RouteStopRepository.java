package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.RouteStop;
import com.example.racehorse_transport.entity.RouteStopId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RouteStopRepository extends JpaRepository<RouteStop, RouteStopId> {

    List<RouteStop> findById_RouteIdOrderById_Seq(Long routeId);

    @Query("SELECT s FROM RouteStop s JOIN FETCH s.location WHERE s.id.routeId = :routeId ORDER BY s.id.seq")
    List<RouteStop> findWithLocation(@Param("routeId") Long routeId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RouteStop s WHERE s.id.routeId = :routeId")
    int deleteByRouteId(@Param("routeId") Long routeId);
}
