package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.HorseDTO;
import com.example.racehorse_transport.dto.HorseJourneyLogDTO;
import com.example.racehorse_transport.dto.UpdateHorseLocationRequest;
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

    List<HorseDTO> list(String username, boolean customer);

    /** Rỗng nếu không có ngựa với id này. */
    Optional<HorseDTO> view(Integer id, String username, boolean customer);

    HorseDTO create(HorseDTO request, String username, boolean customer);

    HorseDTO update(Integer id, HorseDTO request, String username, boolean customer);

    HorseDTO updateLocation(Integer id, UpdateHorseLocationRequest request, String username, boolean customer);

    List<HorseJourneyLogDTO> journeyLogs(Integer id, String username, boolean customer);

    void deleteHorse(Integer id, String username, boolean customer);
}
