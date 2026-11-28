package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HandoverDocument;
import com.example.racehorse_transport.entity.HandoverDocumentId;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HandoverDocumentRepository extends JpaRepository<HandoverDocument, HandoverDocumentId> {
}
