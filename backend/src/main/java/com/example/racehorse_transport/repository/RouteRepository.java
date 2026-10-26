package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Route;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RouteRepository extends JpaRepository<Route, Integer> {
    @Query("SELECT COALESCE(MAX(r.id), 0) FROM Route r")
    Integer findMaxId();

    Optional<Route> findByRouteNameIgnoreCase(String routeName);

    Optional<Route> findBySeedKey(String seedKey);
}
