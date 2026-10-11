package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.LocationRoute;
import com.example.racehorse_transport.entity.LocationRouteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocationRouteRepository extends JpaRepository<LocationRoute, LocationRouteId> {
    @Query("SELECT lr FROM LocationRoute lr WHERE lr.id.routeID = :routeId ORDER BY lr.stopOrder ASC")
    List<LocationRoute> findByRouteId(@Param("routeId") Integer routeId);
}