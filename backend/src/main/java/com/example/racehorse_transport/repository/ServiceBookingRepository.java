package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.ServiceBooking;
import com.example.racehorse_transport.entity.ServiceBookingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceBookingRepository extends JpaRepository<ServiceBooking, ServiceBookingId> {
}
