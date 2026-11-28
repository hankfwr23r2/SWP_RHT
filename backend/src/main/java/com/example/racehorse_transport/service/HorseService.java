package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.horse.HorseCreateRequestDto;
import com.example.racehorse_transport.dto.horse.HorseResponseDto;
import com.example.racehorse_transport.dto.horse.HorseTypeResponseDto;
import com.example.racehorse_transport.dto.horse.HorseUpdateRequestDto;
import java.util.List;

// Hồ sơ ngựa của khách. Chủ sở hữu bảng horse, horse_type: luồng A (A5).
public interface HorseService {

    List<HorseTypeResponseDto> horseTypes();

    List<HorseResponseDto> horses(String username);

    HorseResponseDto horse(String username, Long id);

    HorseResponseDto createHorse(String username, HorseCreateRequestDto request);

    HorseResponseDto updateHorse(String username, Long id, HorseUpdateRequestDto request);
}
