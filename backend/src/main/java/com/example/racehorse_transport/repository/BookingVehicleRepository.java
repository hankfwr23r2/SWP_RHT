package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BookingVehicle;
import com.example.racehorse_transport.entity.BookingVehicleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingVehicleRepository extends JpaRepository<BookingVehicle, BookingVehicleId> {
    List<BookingVehicle> findByBookingID_Id(Integer bookingId);
    List<BookingVehicle> findByVehicleID_Id(Integer vehicleId);
    void deleteByBookingID_Id(Integer bookingId);
}
