package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.ServiceBooking;
import com.example.racehorse_transport.entity.ServiceBookingId;
import com.example.racehorse_transport.repository.ServiceBookingRepository;
import com.example.racehorse_transport.service.ServiceBookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ServiceBookingServiceImpl implements ServiceBookingService {

    private final ServiceBookingRepository serviceBookingRepository;

    @Autowired
    public ServiceBookingServiceImpl(ServiceBookingRepository serviceBookingRepository) {
        this.serviceBookingRepository = serviceBookingRepository;
    }

    @Override
    public List<ServiceBooking> findAll() {
        return serviceBookingRepository.findAll();
    }

    @Override
    public Optional<ServiceBooking> findById(ServiceBookingId id) {
        return serviceBookingRepository.findById(id);
    }

    @Override
    public ServiceBooking save(ServiceBooking entity) {
        return serviceBookingRepository.save(entity);
    }

    @Override
    public void deleteById(ServiceBookingId id) {
        serviceBookingRepository.deleteById(id);
    }
}
