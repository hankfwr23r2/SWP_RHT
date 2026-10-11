package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Service;

import java.util.List;
import java.util.Optional;

public interface ServiceService {
    List<Service> findAll();
    Optional<Service> findById(Integer id);
    Service save(Service entity);
    void deleteById(Integer id);
}
