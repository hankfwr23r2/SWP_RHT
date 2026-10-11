package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.BookingVehicle;
import com.example.racehorse_transport.entity.BookingVehicleId;
import java.util.List;
import java.util.Optional;

public interface BookingVehicleService {
    List<BookingVehicle> findAll();
    Optional<BookingVehicle> findById(BookingVehicleId id);
    BookingVehicle save(BookingVehicle entity);
    void deleteById(BookingVehicleId id);
}
