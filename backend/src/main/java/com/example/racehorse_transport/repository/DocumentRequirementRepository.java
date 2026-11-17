package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.DocumentRequirement;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DocumentRequirementRepository extends JpaRepository<DocumentRequirement, Integer> {
    // Nạp sẵn nhân viên cập nhật để map response không phát sinh N+1 query
    @EntityGraph(attributePaths = {"updatedBy", "updatedBy.user", "location"})
    @Query("SELECT r FROM DocumentRequirement r")
    List<DocumentRequirement> findAllWithUpdatedBy();

    @EntityGraph(attributePaths = {"updatedBy", "updatedBy.user", "location"})
    List<DocumentRequirement> findByStatusOrderByDocumentGroupAscDocumentNameAsc(String status);

    @EntityGraph(attributePaths = {"location"})
    List<DocumentRequirement> findByDocumentGroupAndStatusOrderByDocumentNameAsc(String documentGroup, String status);
}
