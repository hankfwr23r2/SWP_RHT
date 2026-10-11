package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HorseDocument;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HorseDocumentRepository extends JpaRepository<HorseDocument, Integer> {
}
