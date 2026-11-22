package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.DepartureScheduleDay;
import com.example.racehorse_transport.entity.DepartureScheduleDayId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartureScheduleDayRepository extends JpaRepository<DepartureScheduleDay, DepartureScheduleDayId> {

    List<DepartureScheduleDay> findById_ScheduleId(Long scheduleId);
}
