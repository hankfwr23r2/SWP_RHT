package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.DocumentRequirement;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRequirementRepository extends JpaRepository<DocumentRequirement, Long> {

    @EntityGraph(attributePaths = {"rule", "documentType", "gateLocation"})
    List<DocumentRequirement> findByRule_Id(Long ruleId);

    @EntityGraph(attributePaths = {"rule", "documentType", "gateLocation"})
    Optional<DocumentRequirement> findByIdAndRule_Id(Long id, Long ruleId);
}
