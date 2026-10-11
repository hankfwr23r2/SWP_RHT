package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.HorseDocument;

import java.util.List;
import java.util.Optional;

public interface HorseDocumentService {
    List<HorseDocument> findAll();
    Optional<HorseDocument> findById(Integer id);
    HorseDocument save(HorseDocument entity);
    void deleteById(Integer id);
}
