package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Location;

import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.service.LocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class LocationServiceImpl implements LocationService {

    private final LocationRepository locationRepository;

    @Autowired
    public LocationServiceImpl(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Override
    public List<Location> findAll() {
        return locationRepository.findAll();
    }

    @Override
    public Optional<Location> findById(Integer id) {
        return locationRepository.findById(id);
    }

    @Override
    public Location save(Location entity) {
        return locationRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        locationRepository.deleteById(id);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 3: Lộ trình & Điều phối (Flow 3)
    // =========================================================================
    // TODO (Dev 3): CRUD Trạm dừng.
    // - (Đã có sẵn CRUD cơ bản, bổ sung check logic khoảng cách, sức chứa trạm...)

}
