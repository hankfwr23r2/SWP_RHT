package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Service;

import com.example.racehorse_transport.repository.ServiceRepository;
import com.example.racehorse_transport.service.ServiceService;
import org.springframework.beans.factory.annotation.Autowired;
// Removed import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Service
public class ServiceServiceImpl implements ServiceService {

    private final ServiceRepository serviceRepository;

    @Autowired
    public ServiceServiceImpl(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Override
    public List<Service> findAll() {
        return serviceRepository.findAll();
    }

    @Override
    public Optional<Service> findById(Integer id) {
        return serviceRepository.findById(id);
    }

    @Override
    public Service save(Service entity) {
        return serviceRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        serviceRepository.deleteById(id);
    }
}
