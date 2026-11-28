package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HandoverDocument;
import com.example.racehorse_transport.entity.HandoverDocumentId;

import com.example.racehorse_transport.entity.enums.HandoverStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HandoverDocumentRepository extends JpaRepository<HandoverDocument, HandoverDocumentId> {

    List<HandoverDocument> findById_DocumentIdAndHandover_StatusOrderByHandover_HandedAtDescHandover_IdDesc(Long documentId, HandoverStatus status);

    List<HandoverDocument> findById_HandoverId(Long handoverId);
}
