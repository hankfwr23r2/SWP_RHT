package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Horse;

import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.service.HorseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class HorseServiceImpl implements HorseService {

    private final HorseRepository horseRepository;

    @Autowired
    public HorseServiceImpl(HorseRepository horseRepository) {
        this.horseRepository = horseRepository;
    }

    @Override
    public List<Horse> findAll() {
        return horseRepository.findAll();
    }

    @Override
    public Optional<Horse> findById(Integer id) {
        return horseRepository.findById(id);
    }

    @Override
    public Horse save(Horse entity) {
        return horseRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        horseRepository.deleteById(id);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 1 (Lead): Core (Flow 1)
    // =========================================================================
    // TODO (Dev 1): CRUD Ngựa.
    // - (Đã có sẵn CRUD cơ bản, bổ sung logic kiểm tra microchip trùng lặp...)

}
