package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.BookingVehicle;
import com.example.racehorse_transport.entity.BookingVehicleId;
import com.example.racehorse_transport.repository.BookingVehicleRepository;
import com.example.racehorse_transport.service.BookingVehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BookingVehicleServiceImpl implements BookingVehicleService {

    private final BookingVehicleRepository bookingVehicleRepository;

    @Autowired
    public BookingVehicleServiceImpl(BookingVehicleRepository bookingVehicleRepository) {
        this.bookingVehicleRepository = bookingVehicleRepository;
    }

    @Override
    public List<BookingVehicle> findAll() {
        return bookingVehicleRepository.findAll();
    }

    @Override
    public Optional<BookingVehicle> findById(BookingVehicleId id) {
        return bookingVehicleRepository.findById(id);
    }

    @Override
    public BookingVehicle save(BookingVehicle entity) {
        return bookingVehicleRepository.save(entity);
    }

    @Override
    public void deleteById(BookingVehicleId id) {
        bookingVehicleRepository.deleteById(id);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 3: Điều phối (Flow 3, 5)
    // =========================================================================
    // TODO (Dev 3): Gán xe cho chuyến đi.
    // - Viết hàm assignVehicleToRoute(Integer routeId, Integer vehicleId)
    
    // TODO (Dev 3): Xử lý sự cố (Đổi xe khẩn cấp).
    // - Viết hàm changeVehicleEmergency(Integer routeId, Integer newVehicleId, String reason)

}
