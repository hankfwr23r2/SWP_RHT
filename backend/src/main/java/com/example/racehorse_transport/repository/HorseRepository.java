package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Horse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HorseRepository extends JpaRepository<Horse, Integer> {
}
