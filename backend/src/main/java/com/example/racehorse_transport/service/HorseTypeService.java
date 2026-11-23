package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.HorseTypeDto;
import com.example.racehorse_transport.dto.admin.CreateHorseTypeRequestDto;
import com.example.racehorse_transport.dto.admin.UpdateHorseTypeInsuredValueRequestDto;

import java.util.List;

public interface HorseTypeService {
    List<HorseTypeDto> findAll();

    HorseTypeDto create(CreateHorseTypeRequestDto request);

    HorseTypeDto update(Integer id, CreateHorseTypeRequestDto request);

    void delete(Integer id);

    HorseTypeDto updateInsuredValue(Integer id, UpdateHorseTypeInsuredValueRequestDto request);
}
