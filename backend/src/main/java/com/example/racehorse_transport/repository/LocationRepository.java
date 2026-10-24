package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Location;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LocationRepository extends JpaRepository<Location, Integer> {
    @Query("SELECT COALESCE(MAX(l.id), 0) FROM Location l")
    Integer findMaxId();
}
