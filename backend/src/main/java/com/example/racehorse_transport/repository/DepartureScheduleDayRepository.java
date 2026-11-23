package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.DepartureScheduleDay;
import com.example.racehorse_transport.entity.DepartureScheduleDayId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DepartureScheduleDayRepository extends JpaRepository<DepartureScheduleDay, DepartureScheduleDayId> {

    List<DepartureScheduleDay> findById_ScheduleId(Long scheduleId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DepartureScheduleDay d WHERE d.id.scheduleId = :scheduleId")
    int deleteByScheduleId(@Param("scheduleId") Long scheduleId);
}
