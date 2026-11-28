package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.dto.hub.HubOverviewResponseDto;
import com.example.racehorse_transport.service.HubOverviewService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HubOverviewController {
    private final HubOverviewService hubs;

    @GetMapping("/hubs/overview")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ApiResponseDto<List<HubOverviewResponseDto>> hubOverview() {
        return ApiResponseDto.success(hubs.overview(), "Tổng quan các hub");
    }
}
