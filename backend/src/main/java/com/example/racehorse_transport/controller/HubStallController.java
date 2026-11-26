package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.health.HubStallChangeRequestDto;
import com.example.racehorse_transport.dto.health.HubStallCreateRequestDto;
import com.example.racehorse_transport.dto.health.HubStallInventoryResponseDto;
import com.example.racehorse_transport.dto.health.HubStallResponseDto;
import com.example.racehorse_transport.service.HubStallService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HubStallController {
    private final HubStallService stalls;

    @GetMapping("/locations/{hubId}/hub-stalls")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public ApiResponseDto<HubStallInventoryResponseDto> hubStallInventory(@PathVariable Long hubId) {
        return ApiResponseDto.success(stalls.inventory(hubId), "Danh sách ô chuồng");
    }

    @PostMapping("/locations/{hubId}/hub-stalls")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDto<HubStallResponseDto> createHubStall(@PathVariable Long hubId, @Valid @RequestBody HubStallCreateRequestDto request) {
        return ApiResponseDto.success(stalls.create(hubId, request), "Đã thêm ô chuồng");
    }

    @PostMapping("/locations/{hubId}/hub-stalls/{stallId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponseDto<HubStallResponseDto> deactivateHubStall(@PathVariable Long hubId, @PathVariable Long stallId) {
        return ApiResponseDto.success(stalls.deactivate(hubId, stallId), "Đã ngừng dùng ô chuồng");
    }

    @PostMapping("/horse-stays/{stayId}/change-stall")
    @PreAuthorize("hasRole('COORDINATOR')")
    public ApiResponseDto<HubStallResponseDto> changeHubStall(@PathVariable Long stayId, @Valid @RequestBody HubStallChangeRequestDto request) {
        return ApiResponseDto.success(stalls.move(stayId, request), "Đã đổi ô chuồng");
    }
}
