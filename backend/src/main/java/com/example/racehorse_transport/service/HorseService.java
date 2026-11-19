package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.HorseDto;
import com.example.racehorse_transport.dto.HorseJourneyLogDto;
import com.example.racehorse_transport.dto.UpdateHorseLocationRequestDto;
import com.example.racehorse_transport.entity.Horse;

import java.util.List;
import java.util.Optional;

/**
 * Hồ sơ ngựa. Các hàm nhận {@code username} và {@code customer}: khi người gọi là khách hàng,
 * chỉ được xem và sửa ngựa của chính mình; vai trò khác không bị giới hạn theo chủ sở hữu.
 */
public interface HorseService {
    List<Horse> findAll();
    Optional<Horse> findById(Integer id);
    Horse save(Horse entity);
    void deleteById(Integer id);

    List<HorseDto> list(String username, boolean customer);

    /** Rỗng nếu không có ngựa với id này. */
    Optional<HorseDto> view(Integer id, String username, boolean customer);

    HorseDto create(HorseDto request, String username, boolean customer);

    HorseDto update(Integer id, HorseDto request, String username, boolean customer);

    HorseDto updateLocation(Integer id, UpdateHorseLocationRequestDto request, String username, boolean customer);

    List<HorseJourneyLogDto> journeyLogs(Integer id, String username, boolean customer);

    void deleteHorse(Integer id, String username, boolean customer);
}
