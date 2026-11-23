package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.enums.DepartureStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DepartureRepository extends JpaRepository<Departure, Long> {

    // SELECT ... FOR UPDATE: giữ dòng chuyến tới hết transaction khi đặt hoặc nhả khoang
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Departure d WHERE d.id = :id")
    Optional<Departure> findForUpdate(@Param("id") Long id);

    @Query("SELECT d FROM Departure d JOIN FETCH d.route r JOIN FETCH r.origin JOIN FETCH r.destination WHERE d.id = :id")
    Optional<Departure> findWithRoute(@Param("id") Long id);

    // Sinh chuyến từ lịch; chạy lại nhiều lần (hoặc nhiều máy chủ cùng chạy) cũng không tạo trùng
    @Modifying
    @Query(value = """
            INSERT INTO departure (schedule_id, route_id, departure_date, planned_depart_at, booking_cutoff_at,
                                   capacity_stalls, booked_stalls, status)
            VALUES (:scheduleId, :routeId, :departureDate, :plannedDepartAt, :bookingCutoffAt, :capacityStalls, 0, 'OPEN')
            ON CONFLICT ON CONSTRAINT uk_departure_schedule_date DO NOTHING""", nativeQuery = true)
    int insertIfAbsent(@Param("scheduleId") Long scheduleId, @Param("routeId") Long routeId,
                       @Param("departureDate") LocalDate departureDate, @Param("plannedDepartAt") Instant plannedDepartAt,
                       @Param("bookingCutoffAt") Instant bookingCutoffAt, @Param("capacityStalls") int capacityStalls);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Departure d SET d.status = :to WHERE d.status = :from AND d.bookingCutoffAt <= :now")
    int changeStatusWhenCutoffPassed(@Param("from") DepartureStatus from, @Param("to") DepartureStatus to, @Param("now") Instant now);

    // routeId, status null nghĩa là không lọc
    @Query("""
            SELECT d FROM Departure d JOIN FETCH d.route r JOIN FETCH r.origin JOIN FETCH r.destination
            WHERE (:routeId IS NULL OR r.id = :routeId) AND (:status IS NULL OR d.status = :status)
              AND d.departureDate BETWEEN :fromDate AND :toDate
            ORDER BY d.plannedDepartAt""")
    List<Departure> search(@Param("routeId") Long routeId, @Param("status") DepartureStatus status,
                           @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    // Chuyến còn nhận đặt chỗ trên các tuyến cho trước, đủ số khoang cần
    @Query("""
            SELECT d FROM Departure d JOIN FETCH d.route r JOIN FETCH r.origin JOIN FETCH r.destination
            WHERE r.id IN :routeIds AND d.status = :status AND d.bookingCutoffAt > :now AND d.departureDate >= :fromDate
              AND d.capacityStalls - d.bookedStalls >= :stalls
            ORDER BY d.plannedDepartAt""")
    List<Departure> findBookable(@Param("routeIds") Collection<Long> routeIds, @Param("status") DepartureStatus status,
                                 @Param("now") Instant now, @Param("fromDate") LocalDate fromDate, @Param("stalls") int stalls);
}
