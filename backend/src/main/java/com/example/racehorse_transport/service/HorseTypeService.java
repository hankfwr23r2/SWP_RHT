package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.HorseTypeDTO;
import com.example.racehorse_transport.dto.admin.CreateHorseTypeRequest;
import com.example.racehorse_transport.dto.admin.UpdateHorseTypeInsuredValueRequest;

import java.util.List;

public interface HorseTypeService {
    List<HorseTypeDTO> findAll();

    HorseTypeDTO create(CreateHorseTypeRequest request);

    HorseTypeDTO update(Integer id, CreateHorseTypeRequest request);

    void delete(Integer id);

    HorseTypeDTO updateInsuredValue(Integer id, UpdateHorseTypeInsuredValueRequest request);
}
