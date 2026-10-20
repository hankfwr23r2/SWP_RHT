package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.HorseTypeDTO;
import com.example.racehorse_transport.entity.HorseType;
import com.example.racehorse_transport.repository.HorseTypeRepository;
import com.example.racehorse_transport.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/horse-types")
@RequiredArgsConstructor
public class HorseTypeController {

    private final HorseTypeRepository horseTypeRepository;

    @PreAuthorize("hasAnyRole('CUSTOMER', 'SPECIALIST', 'INSPECTOR', 'MANAGER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<HorseTypeDTO>>> getAllHorseTypes() {
        List<HorseTypeDTO> horseTypes = horseTypeRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(horseTypes, "Lấy danh sách giống ngựa thành công"));
    }

    private HorseTypeDTO toDto(HorseType horseType) {
        return new HorseTypeDTO(horseType.getId(), horseType.getTypeName(), horseType.getDescription());
    }
}
