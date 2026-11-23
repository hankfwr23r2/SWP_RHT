package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.DepartureSchedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepartureScheduleRepository extends JpaRepository<DepartureSchedule, Long> {

    // routeId null nghĩa là mọi tuyến
    @Query("""
            SELECT s FROM DepartureSchedule s JOIN FETCH s.route
            WHERE (:routeId IS NULL OR s.route.id = :routeId)
            ORDER BY s.route.code, s.departureTime""")
    List<DepartureSchedule> findWithRoute(@Param("routeId") Long routeId);

    @Query("SELECT s FROM DepartureSchedule s JOIN FETCH s.route WHERE s.id = :id")
    Optional<DepartureSchedule> findWithRouteById(@Param("id") Long id);
}
