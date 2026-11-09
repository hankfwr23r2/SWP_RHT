package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.InsuranceDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsuranceDocumentRepository extends JpaRepository<InsuranceDocument, Long> {
    List<InsuranceDocument> findByInsurance_IdOrderBySortOrderAscIdAsc(Long insuranceId);
}
