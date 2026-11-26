package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.DocumentRequirement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRequirementRepository extends JpaRepository<DocumentRequirement, Long> {

    List<DocumentRequirement> findByRule_Id(Long ruleId);
}
