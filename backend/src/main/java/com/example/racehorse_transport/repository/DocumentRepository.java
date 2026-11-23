package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Document;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
}
