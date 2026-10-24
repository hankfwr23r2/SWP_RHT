package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceRepository extends JpaRepository<Service, Integer> {
    @Query("SELECT COALESCE(MAX(s.id), 0) FROM Service s")
    Integer findMaxId();

    List<Service> findByStatusIgnoreCaseOrderByIdAsc(String status);
}
