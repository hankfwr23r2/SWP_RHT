package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.ServiceBooking;
import com.example.racehorse_transport.entity.ServiceBookingId;
import java.util.List;
import java.util.Optional;

public interface ServiceBookingService {
    List<ServiceBooking> findAll();
    Optional<ServiceBooking> findById(ServiceBookingId id);
    ServiceBooking save(ServiceBooking entity);
    void deleteById(ServiceBookingId id);
}
