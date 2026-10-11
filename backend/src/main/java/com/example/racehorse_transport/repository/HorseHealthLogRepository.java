package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HorseHealthLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HorseHealthLogRepository extends JpaRepository<HorseHealthLog, Integer> {
    @Query("SELECT h FROM HorseHealthLog h WHERE h.horseID.id = :horseId")
    List<HorseHealthLog> findByHorseId(@Param("horseId") Integer horseId);

    @Query("SELECT h FROM HorseHealthLog h WHERE h.bookingID.id = :bookingId")
    List<HorseHealthLog> findByBookingId(@Param("bookingId") Integer bookingId);
}