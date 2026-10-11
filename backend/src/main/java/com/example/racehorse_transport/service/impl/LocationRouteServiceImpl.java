package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.LocationRoute;
import com.example.racehorse_transport.entity.LocationRouteId;
import com.example.racehorse_transport.repository.LocationRouteRepository;
import com.example.racehorse_transport.service.LocationRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LocationRouteServiceImpl implements LocationRouteService {

    private final LocationRouteRepository locationRouteRepository;

    @Autowired
    public LocationRouteServiceImpl(LocationRouteRepository locationRouteRepository) {
        this.locationRouteRepository = locationRouteRepository;
    }

    @Override
    public List<LocationRoute> findAll() {
        return locationRouteRepository.findAll();
    }

    @Override
    public Optional<LocationRoute> findById(LocationRouteId id) {
        return locationRouteRepository.findById(id);
    }

    @Override
    public LocationRoute save(LocationRoute entity) {
        return locationRouteRepository.save(entity);
    }

    @Override
    public void deleteById(LocationRouteId id) {
        locationRouteRepository.deleteById(id);
    }
}
