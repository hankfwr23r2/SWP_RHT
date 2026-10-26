package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.DocumentRequirement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRequirementRepository extends JpaRepository<DocumentRequirement, Integer> {
    List<DocumentRequirement> findByStatusOrderByDocumentGroupAscDocumentNameAsc(String status);
    List<DocumentRequirement> findByDocumentGroupAndStatusOrderByDocumentNameAsc(String documentGroup, String status);
}
