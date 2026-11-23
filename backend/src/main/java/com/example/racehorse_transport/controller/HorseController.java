package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.horse.HorseCreateRequestDto;
import com.example.racehorse_transport.dto.horse.HorseResponseDto;
import com.example.racehorse_transport.dto.horse.HorseTypeResponseDto;
import com.example.racehorse_transport.dto.horse.HorseUpdateRequestDto;
import com.example.racehorse_transport.service.HorseService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class HorseController {

    private final HorseService horses;

    @GetMapping("/horse-types")
    public ApiResponseDto<List<HorseTypeResponseDto>> listHorseTypes() {
        return ApiResponseDto.success(horses.horseTypes(), "Danh sách loại ngựa");
    }

    @GetMapping("/horses")
    public ApiResponseDto<List<HorseResponseDto>> listHorses(Authentication actor) {
        return ApiResponseDto.success(horses.horses(actor.getName()), "Danh sách ngựa");
    }

    @GetMapping("/horses/{id}")
    public ApiResponseDto<HorseResponseDto> getHorse(Authentication actor, @PathVariable Long id) {
        return ApiResponseDto.success(horses.horse(actor.getName(), id), "Hồ sơ ngựa");
    }

    @PostMapping("/horses")
    public ResponseEntity<ApiResponseDto<HorseResponseDto>> createHorse(
            Authentication actor, @Valid @RequestBody HorseCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success(horses.createHorse(actor.getName(), request), "Đã thêm hồ sơ ngựa"));
    }

    @PutMapping("/horses/{id}")
    public ApiResponseDto<HorseResponseDto> updateHorse(
            Authentication actor, @PathVariable Long id, @Valid @RequestBody HorseUpdateRequestDto request) {
        return ApiResponseDto.success(horses.updateHorse(actor.getName(), id, request), "Đã cập nhật hồ sơ ngựa");
    }
}
