package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.dispatch.AgentLegRequestDto;
import com.example.racehorse_transport.dto.dispatch.HorseLegResponseDto;
import com.example.racehorse_transport.service.AgentLegService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Chặng do đại lý nước ngoài chở: điều phối lập chặng và nhập hộ mốc đại lý báo (GĐ7.4–7.5)
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('COORDINATOR')")
public class AgentLegController {

    private final AgentLegService agentLegs;

    @PostMapping("/booking-horses/{id}/agent-legs")
    public ResponseEntity<ApiResponseDto<HorseLegResponseDto>> create(@PathVariable Long id, @Valid @RequestBody AgentLegRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseDto.success(agentLegs.create(id, request), "Đã lập chặng đại lý"));
    }

    @PostMapping("/agent-legs/{id}/cancel")
    public ApiResponseDto<Void> cancel(@PathVariable Long id) {
        agentLegs.cancel(id);
        return ApiResponseDto.success(null, "Đã gỡ chặng đại lý");
    }

    @PostMapping("/agent-legs/{id}/start")
    public ApiResponseDto<HorseLegResponseDto> start(@PathVariable Long id) {
        return ApiResponseDto.success(agentLegs.start(id), "Đã ghi đại lý nhận ngựa");
    }

    @PostMapping("/agent-legs/{id}/complete")
    public ApiResponseDto<HorseLegResponseDto> complete(@PathVariable Long id, Authentication authentication) {
        return ApiResponseDto.success(agentLegs.complete(id, authentication.getName()), "Đã ghi đại lý giao ngựa");
    }
}
