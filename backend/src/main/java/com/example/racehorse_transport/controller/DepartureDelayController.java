package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.departure.DepartureDelayRequestDto;
import com.example.racehorse_transport.dto.departure.DepartureResponseDto;
import com.example.racehorse_transport.service.DepartureDelayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/departures/{id}")
@RequiredArgsConstructor
public class DepartureDelayController {
    private final DepartureDelayService delays;

    @PostMapping("/delay")
    @PreAuthorize("hasRole('MANAGER')")
    public ApiResponseDto<DepartureResponseDto> delay(@PathVariable Long id, @Valid @RequestBody DepartureDelayRequestDto request) {
        return ApiResponseDto.success(delays.revise(id, request), "Đã dời giờ chuyến.");
    }
}
