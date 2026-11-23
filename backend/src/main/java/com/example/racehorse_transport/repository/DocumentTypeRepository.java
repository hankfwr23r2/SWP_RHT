package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.DocumentType;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {

    Optional<DocumentType> findByCode(String code);
}
