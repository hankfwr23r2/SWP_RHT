package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Location;

import java.util.List;
import java.util.Optional;

public interface LocationService {
    List<Location> findAll();
    Optional<Location> findById(Integer id);
    Location save(Location entity);
    void deleteById(Integer id);
}
