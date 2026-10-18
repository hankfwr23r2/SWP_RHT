package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Regulation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegulationRepository extends JpaRepository<Regulation, Integer> {
    List<Regulation> findByStatus(String status);
    List<Regulation> findByLocationID_IdAndStatus(Integer locationId, String status);
    List<Regulation> findByLocationIDIsNullAndStatus(String status);
    List<Regulation> findByCountryAndStatus(String country, String status);
    Optional<Regulation> findByDocumentTypeAndLocationID_Id(String documentType, Integer locationId);
    Optional<Regulation> findByDocumentTypeAndLocationIDIsNull(String documentType);
}
