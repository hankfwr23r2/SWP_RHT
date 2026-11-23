package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Route;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {

    Optional<Route> findByCode(String code);
}
