package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.DepartureScheduleDay;
import com.example.racehorse_transport.entity.DepartureSchedule;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.ScheduleStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepartureScheduleRepository extends JpaRepository<DepartureSchedule, Long> {

    // routeId null nghĩa là mọi tuyến
    @Query("""
            SELECT s FROM DepartureSchedule s JOIN FETCH s.baseVehicleClass JOIN FETCH s.route
            WHERE (:routeId IS NULL OR s.route.id = :routeId)
            ORDER BY s.route.code, s.departureTime""")
    List<DepartureSchedule> findWithRoute(@Param("routeId") Long routeId);

    @Query("SELECT s FROM DepartureSchedule s JOIN FETCH s.baseVehicleClass JOIN FETCH s.route WHERE s.id = :id")
    Optional<DepartureSchedule> findWithRouteById(@Param("id") Long id);

    @Query("SELECT day FROM DepartureScheduleDay day")
    List<DepartureScheduleDay> findScheduleDays();

    // Lịch sinh được chuyến: lịch và tuyến đều đang chạy
    @Query("SELECT s FROM DepartureSchedule s JOIN FETCH s.baseVehicleClass JOIN FETCH s.route r WHERE s.status = :status AND r.status = :routeStatus")
    List<DepartureSchedule> findRunnable(@Param("status") ScheduleStatus status, @Param("routeStatus") ActiveStatus routeStatus);
}
