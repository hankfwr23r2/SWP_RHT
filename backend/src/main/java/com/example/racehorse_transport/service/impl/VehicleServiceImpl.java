package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Vehicle;

import com.example.racehorse_transport.repository.VehicleRepository;
import com.example.racehorse_transport.service.VehicleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;

    @Autowired
    public VehicleServiceImpl(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    public List<Vehicle> findAll() {
        return vehicleRepository.findAll();
    }

    @Override
    public Optional<Vehicle> findById(Integer id) {
        return vehicleRepository.findById(id);
    }

    @Override
    public Vehicle save(Vehicle entity) {
        return vehicleRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        vehicleRepository.deleteById(id);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 3: Lộ trình & Điều phối (Flow 3)
    // =========================================================================
    // TODO (Dev 3): CRUD Phương tiện.
    // - (Đã có sẵn CRUD cơ bản, cần bổ sung logic check xe rảnh/bận, bão dưỡng...)

}
