package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Regulation;

import java.util.List;
import java.util.Optional;

public interface RegulationService {
    List<Regulation> findAll();
    Optional<Regulation> findById(Integer id);
    Regulation save(Regulation entity);
    void deleteById(Integer id);
}
