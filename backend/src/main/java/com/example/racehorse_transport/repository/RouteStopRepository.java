package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.RouteStop;
import com.example.racehorse_transport.entity.RouteStopId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteStopRepository extends JpaRepository<RouteStop, RouteStopId> {

    List<RouteStop> findById_RouteIdOrderById_Seq(Long routeId);
}
