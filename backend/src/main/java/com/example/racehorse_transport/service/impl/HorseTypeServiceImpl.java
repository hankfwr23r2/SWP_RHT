package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.HorseType;

import com.example.racehorse_transport.repository.HorseTypeRepository;
import com.example.racehorse_transport.service.HorseTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class HorseTypeServiceImpl implements HorseTypeService {

    private final HorseTypeRepository horseTypeRepository;

    @Autowired
    public HorseTypeServiceImpl(HorseTypeRepository horseTypeRepository) {
        this.horseTypeRepository = horseTypeRepository;
    }

    @Override
    public List<HorseType> findAll() {
        return horseTypeRepository.findAll();
    }

    @Override
    public Optional<HorseType> findById(Integer id) {
        return horseTypeRepository.findById(id);
    }

    @Override
    public HorseType save(HorseType entity) {
        return horseTypeRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        horseTypeRepository.deleteById(id);
    }
}
