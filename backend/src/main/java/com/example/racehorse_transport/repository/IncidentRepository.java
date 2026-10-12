package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Integer> {
    List<Incident> findByBookingID_Id(Integer bookingId);
    List<Incident> findByBookingID_IdAndCostBearer(Integer bookingId, String costBearer);
    List<Incident> findByStatus(String status);
}
