package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.QuotationLine;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotationLineRepository extends JpaRepository<QuotationLine, Long> {

    List<QuotationLine> findByQuotation_IdOrderByLineNumber(Long quotationId);
}
