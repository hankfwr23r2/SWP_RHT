package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Vehicle;

import java.util.List;
import java.util.Optional;

public interface VehicleService {
    List<Vehicle> findAll();
    Optional<Vehicle> findById(Integer id);
    Vehicle save(Vehicle entity);
    void deleteById(Integer id);
}
