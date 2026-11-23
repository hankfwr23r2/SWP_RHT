package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.admin.SystemConfigResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
import com.example.racehorse_transport.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/public/configs")
@RequiredArgsConstructor
public class PublicSystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<SystemConfigResponseDto>>> getPublicConfigs() {
        List<SystemConfigResponseDto> publicConfigs = systemConfigService.getAllConfigs().stream()
                .map(config -> SystemConfigResponseDto.builder()
                        .configKey(config.getConfigKey())
                        .configName(config.getConfigName())
                        .configValue(config.getConfigValue())
                        .unit(config.getUnit())
                        .configGroup(config.getConfigGroup())
                        .description(config.getDescription())
                        .updatedAt(config.getUpdatedAt())
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponseDto.success(publicConfigs,
                "Lấy tham số nghiệp vụ công khai thành công."));
    }
}
