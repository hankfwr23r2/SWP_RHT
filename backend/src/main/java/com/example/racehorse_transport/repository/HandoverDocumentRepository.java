package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HandoverDocument;
import com.example.racehorse_transport.entity.HandoverDocumentId;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandoverDocumentRepository extends JpaRepository<HandoverDocument, HandoverDocumentId> {

    List<HandoverDocument> findById_HandoverId(Long handoverId);
}
