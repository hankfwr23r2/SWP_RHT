package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.DocumentType;
import com.example.racehorse_transport.entity.enums.DocumentSubject;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {

    Optional<DocumentType> findByCode(String code);

    List<DocumentType> findByOwnerTypeOrderById(DocumentSubject ownerType);
}
