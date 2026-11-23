package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.DepartureSchedule;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartureScheduleRepository extends JpaRepository<DepartureSchedule, Long> {
}
